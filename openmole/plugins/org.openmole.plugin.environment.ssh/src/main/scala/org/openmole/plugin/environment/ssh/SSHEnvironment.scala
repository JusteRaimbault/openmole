/*
 * Copyright (C) 2011 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openmole.plugin.environment.ssh

import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

import freedsl.dsl._
import freedsl.system.SystemInterpreter
import gridscale.ssh
import org.openmole.core.authentication.AuthenticationStore
import org.openmole.core.communication.storage.TransferOptions
import org.openmole.core.preference.{ ConfigurationLocation, Preference }
import org.openmole.core.threadprovider.{ IUpdatable, Updater }
import org.openmole.core.workflow.dsl._
import org.openmole.core.workflow.execution._
import org.openmole.core.workspace._
import org.openmole.plugin.environment.batch.control._
import org.openmole.plugin.environment.batch.environment._
import org.openmole.plugin.environment.batch.jobservice._
import org.openmole.plugin.environment.batch.storage._
import org.openmole.plugin.environment.gridscale.{ GridScaleJobService, LogicalLinkStorage }
import org.openmole.tool.crypto._
import org.openmole.tool.lock._
import squants.information._
import squants.time.TimeConversions._
import freedsl.dsl._
import org.openmole.tool.cache.Lazy

import scala.ref.WeakReference

object SSHEnvironment {

  val MaxConnections = ConfigurationLocation("SSHEnvironment", "MaxConnections", Some(10))
  val MaxOperationsByMinute = ConfigurationLocation("SSHEnvironment", "MaxOperationByMinute", Some(500))
  val UpdateInterval = ConfigurationLocation("SSHEnvironment", "UpdateInterval", Some(10 seconds))
  val TimeOut = ConfigurationLocation("SSHEnvironment", "Timeout", Some(1 minutes))

  def apply(
    user:                 String,
    host:                 String,
    slots:                Int,
    port:                 Int                           = 22,
    sharedDirectory:      OptionalArgument[String]      = None,
    workDirectory:        OptionalArgument[String]      = None,
    openMOLEMemory:       OptionalArgument[Information] = None,
    threads:              OptionalArgument[Int]         = None,
    storageSharedLocally: Boolean                       = false,
    name:                 OptionalArgument[String]      = None
  )(implicit services: BatchEnvironment.Services, cypher: Cypher, authenticationStore: AuthenticationStore, varName: sourcecode.Name) = {
    import services._
    new SSHEnvironment(
      user = user,
      host = host,
      slots = slots,
      port = port,
      sharedDirectory = sharedDirectory,
      workDirectory = workDirectory,
      openMOLEMemory = openMOLEMemory,
      threads = threads,
      storageSharedLocally = storageSharedLocally,
      name = Some(name.getOrElse(varName.value)),
      authentication = SSHAuthentication.find(user, host, port).apply
    )
  }

  class Updater(environemnt: WeakReference[SSHEnvironment[_]]) extends IUpdatable {

    var stop = false

    def update() =
      if (stop) false
      else environemnt.get match {
        case Some(env) ⇒
          val nbSubmit = env.slots - env.numberOfRunningJobs
          val toSubmit = env.queuesLock { env.jobsStates.collect { case (job, Queued(desc)) ⇒ job → desc }.take(nbSubmit) }

          for {
            (job, desc) ← toSubmit
          } env.submit(job, desc)

          import freedsl.tool._
          import freedsl.dsl._

          // Clean submitted
          //              val keep = js.submitted.filter(j ⇒ j.state == RUNNING || j.state == READY || j.state == SUBMITTED)
          //              js.submitted.clear()
          //              js.submitted.pushAll(keep)
          //
          //              //Clean queue
          //              val keepQueued = js.queue.filter(j ⇒ j.state == READY || j.state == SUBMITTED)
          //              js.queue.clear()
          //              js.queue.pushAll(keepQueued)
          //
          //              var numberToSubmit = js.nbSlots - js.submitted.size
          //
          //              val toSubmit = mutable.Stack[SSHBatchJob]()
          //              while (!js.queue.isEmpty && numberToSubmit > 0) {
          //                val j = js.queue.pop()
          //                toSubmit.push(j)
          //                numberToSubmit -= 1
          //                j
          //              }
          //
          //              toSubmit

          //toSubmit.foreach(_.submit)
          !stop
        case None ⇒ false
      }
  }

  implicit def asSSHServer[A: ssh.SSHAuthentication] = new AsSSHServer[SSHEnvironment[A]] {
    override def apply(t: SSHEnvironment[A]) = ssh.SSHServer(t.host, t.port, t.timeout)(t.authentication)
  }

  case class SSHJob(id: Long) extends AnyVal

  sealed trait SSHRunState
  case class Queued(description: gridscale.ssh.SSHJobDescription) extends SSHRunState
  case class Submitted(pid: gridscale.ssh.JobId) extends SSHRunState
  case object Failed extends SSHRunState

  implicit def isJobService[A]: JobServiceInterface[SSHEnvironment[A]] = new JobServiceInterface[SSHEnvironment[A]] {
    override type J = SSHJob

    override def submit(env: SSHEnvironment[A], serializedJob: SerializedJob): BatchJob[J] = env.register(serializedJob)
    //    {
    //      val (remoteScript, result) = buildScript(serializedJob)
    //
    //      val _jobDescription = gridscale.ssh.SSHJobDescription (
    //        command = s"/bin/bash $remoteScript",
    //        workDirectory = sharedFS.root
    //      )
    //
    //      val sshBatchJob = new SSHBatchJob {
    //        val jobService = js
    //        val jobDescription = _jobDescription
    //        val resultPath = result
    //      }
    //
    //      SSHJobService.Log.logger.fine(s"SSHJobService: Queueing /bin/bash $remoteScript in directory ${sharedFS.root}")
    //
    //      queuesLock { queue.push(sshBatchJob) }
    //
    //      sshBatchJob
    //    }
    //
    override def state(env: SSHEnvironment[A], j: J): ExecutionState.ExecutionState = env.state(j)
    override def delete(env: SSHEnvironment[A], j: J): Unit = env.delete(j)
    override def stdOutErr(js: SSHEnvironment[A], j: SSHJob) = js.stdOutErr(j)
  }

}

import SSHEnvironment._

class SSHEnvironment[A: gridscale.ssh.SSHAuthentication](
    val user:                 String,
    val host:                 String,
    val slots:                Int,
    val port:                 Int,
    val sharedDirectory:      Option[String],
    val workDirectory:        Option[String],
    val openMOLEMemory:       Option[Information],
    override val threads:     Option[Int],
    val storageSharedLocally: Boolean,
    override val name:        Option[String],
    val authentication:       A
)(implicit val services: BatchEnvironment.Services) extends BatchEnvironment /*with SSHPersistentStorage*/ { env ⇒

  //  type JS = SSHJobService
  //
  //  def id = new URI("ssh", env.user, env.host, env.port, null, null, null).toString
  //
  //  val usageControl =
  //    new LimitedAccess(
  //      preference(SSHEnvironment.MaxConnections),
  //      preference(SSHEnvironment.MaxOperationsByMinute)
  //    )
  //
  //  import services.threadProvider
  //
  //  val jobService = SSHJobService(
  //    slots = nbSlots,
  //    sharedFS = storage,
  //    environment = env,
  //    workDirectory = env.workDirectory,
  //    credential = credential,
  //    host = host,
  //    user = user,
  //    port = port
  //  )
  //
  //  override def updateInterval = UpdateInterval.fixed(preference(SSHEnvironment.UpdateInterval))
  //

  lazy val jobUpdater = new SSHEnvironment.Updater(WeakReference(this))
  override def start() = {
    super.start()
    import services.threadProvider
    Updater.delay(jobUpdater, services.preference(SSHEnvironment.UpdateInterval))
  }

  implicit val sshInterpreter = new gridscale.ssh.SSHInterpreter()
  implicit val systemInterpreter = SystemInterpreter()

  def timeout = services.preference(SSHEnvironment.TimeOut)

  override def stop() = {
    try super.stop()
    finally {
      jobUpdater.stop = true
      sshInterpreter.close()
    }
    // jobService.stop()
  }

  lazy val usageControl =
    new LimitedAccess(
      services.preference(SSHEnvironment.MaxConnections),
      services.preference(SSHEnvironment.MaxOperationsByMinute)
    )

  // TODO take shared locally into account
  lazy val storageService = {
    val storageInterface = StorageInterface[SSHEnvironment[A]]

    val root = sharedDirectory match {
      case Some(p) ⇒ p
      case None ⇒
        val home = storageInterface.home(env)
        storageInterface.child(env, home, ".openmole/.tmp/ssh/")
    }

    val remoteStorage = StorageInterface.remote(LogicalLinkStorage())(LogicalLinkStorage.isStorage(gridscale.local.LocalInterpreter()))
    def id = new URI("ssh", user, host, port, root, null, null).toString

    def isConnectionError(t: Throwable) = t match {
      case _: gridscale.ssh.ConnectionError ⇒ true
      case _: gridscale.authentication.AuthenticationException ⇒ true
      case _ ⇒ false
    }

    new StorageService(env, root, id, env, remoteStorage, usageControl, isConnectionError)
  }

  override def trySelectStorage(files: ⇒ Vector[File]) = BatchEnvironment.trySelectSingleStorage(storageService)

  val installMap = collection.mutable.Map[Runtime, String]()

  def installRuntime(runtime: Runtime) = installMap.synchronized {
    installMap.get(runtime) match {
      case Some(p) ⇒ p
      case None ⇒
        import services._
        val sshServer = gridscale.ssh.SSHServer(host, port, preference(SSHEnvironment.TimeOut))(authentication)
        val p = SharedStorage.installRuntime(runtime, storageService, sshServer)
        installMap.put(runtime, p)
        p
    }
  }

  def buildScript(serializedJob: SerializedJob) = {
    import services._
    SharedStorage.buildScript(env.installRuntime, env.workDirectory, env.openMOLEMemory, env.threads, serializedJob, env.storageService)
  }

  def register(serializedJob: SerializedJob) = {
    val (remoteScript, result, workDirectory) = env.buildScript(serializedJob)
    val jobDescription = gridscale.ssh.SSHJobDescription(
      command = s"/bin/bash $remoteScript",
      workDirectory = workDirectory
    )

    val registred = queuesLock {
      val job = SSHEnvironment.SSHJob(jobId.getAndIncrement())
      jobsStates.put(job, Queued(jobDescription))
      job
    }

    BatchJob(registred, result)
  }

  def submit(job: SSHEnvironment.SSHJob, description: gridscale.ssh.SSHJobDescription) =
    try {
      val id = gridscale.ssh.submit[DSL](env, description).eval
      queuesLock { jobsStates.put(job, Submitted(id)) }
    }
    catch {
      case t: Throwable ⇒
        queuesLock { jobsStates.put(job, Failed) }
        throw t
    }

  def state(job: SSHEnvironment.SSHJob) = queuesLock {
    jobsStates.get(job) match {
      case None                ⇒ ExecutionState.DONE
      case Some(state: Queued) ⇒ ExecutionState.SUBMITTED
      case Some(Failed)        ⇒ ExecutionState.FAILED
      case Some(Submitted(id)) ⇒ GridScaleJobService.translateStatus(gridscale.ssh.state[DSL](env, id).eval)
    }
  }

  def delete(job: SSHEnvironment.SSHJob): Unit = {
    val jobState = queuesLock { jobsStates.remove(job) }
    jobState match {
      case Some(Submitted(id)) ⇒ gridscale.ssh.clean[DSL](env, id).eval
      case _                   ⇒
    }
  }

  def stdOutErr(j: SSHJob) = {
    val jobState = queuesLock { jobsStates.get(j) }
    jobState match {
      case Some(Submitted(id)) ⇒
        val op =
          for {
            o ← gridscale.ssh.stdOut[DSL](env, id)
            e ← gridscale.ssh.stdErr[DSL](env, id)
          } yield (o, e)
        op.eval
      case _ ⇒ ("", "")
    }
  }

  def numberOfRunningJobs: Int = {
    import freedsl.dsl._
    import cats.implicits._
    val sshJobIds = env.queuesLock { env.jobsStates.toSeq.collect { case (j, Submitted(id)) ⇒ id } }
    sshJobIds.toList.traverse(id ⇒ gridscale.ssh.SSHJobDescription.jobIsRunning[DSL](env, id)).eval.count(_ == true)
  }

  val queuesLock = new ReentrantLock()
  val jobsStates = collection.mutable.TreeMap[SSHEnvironment.SSHJob, SSHRunState]()(Ordering.by(_.id))
  val jobId = new AtomicLong()

  type PID = Int
  val submittedJobs = collection.mutable.Map[SSHEnvironment.SSHJob, PID]()

  lazy val jobService = new BatchJobService(env, usageControl)
  override def trySelectJobService() = BatchEnvironment.trySelectSingleJobService(jobService)
}

//class SSHEnvironment[A: gridscale.ssh.SSHAuthentication](
//                                                          val user:                 String,
//                                                          val host:                 String,
//                                                          val nbSlots:              Int,
//                                                          val port:        Int,
//                                                          val sharedDirectory:      Option[String],
//                                                          val workDirectory:        Option[String],
//                                                          val openMOLEMemory:       Option[Information],
//                                                          override val threads:     Option[Int],
//                                                          val storageSharedLocally: Boolean,
//                                                          override val name:        Option[String]
//                                                        )(val credential: A)(implicit val services: BatchEnvironment.Services) extends BatchEnvironment /*with SSHPersistentStorage*/ { env ⇒
//
//  type JS = SSHJobService
//
//  def id = new URI("ssh", env.user, env.host, env.port, null, null, null).toString
//
//  val usageControl =
//    new LimitedAccess(
//      preference(SSHEnvironment.MaxConnections),
//      preference(SSHEnvironment.MaxOperationsByMinute)
//    )
//
//  import services.threadProvider
//
//  val jobService = SSHJobService(
//    slots = nbSlots,
//    sharedFS = storage,
//    environment = env,
//    workDirectory = env.workDirectory,
//    credential = credential,
//    host = host,
//    user = user,
//    port = port
//  )
//
//  override def updateInterval = UpdateInterval.fixed(preference(SSHEnvironment.UpdateInterval))
//
//  override def start() = {
//    super.start()
//    jobService.start()
//  }
//
//  override def stop() = {
//    super.stop()
//    jobService.stop()
//  }
//
//}