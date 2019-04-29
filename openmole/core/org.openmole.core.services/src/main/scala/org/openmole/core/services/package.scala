package org.openmole.core

import java.io.PrintStream

import org.openmole.core.authentication._
import org.openmole.core.event._
import org.openmole.core.fileservice._
import org.openmole.core.preference._
import org.openmole.core.replication._
import org.openmole.core.serializer._
import org.openmole.core.threadprovider._
import org.openmole.core.workspace._
import org.openmole.tool.crypto._
import org.openmole.tool.random._
import org.openmole.tool.file._
import org.openmole.core.outputredirection._
import org.openmole.core.networkservice._

package object services {

  /**
   * Methods to get implicit services (workspace, files, random provider, network, etc.)
   */
  object Services {

    /**
     * Execute a function with services provided
     * @param workspace
     * @param password
     * @param httpProxy
     * @param f
     * @tparam T
     * @return
     */
    def withServices[T](workspace: File, password: String, httpProxy: Option[String])(f: Services ⇒ T) = {
      val services = Services(workspace, password, httpProxy)
      try f(services)
      finally dispose(services)
    }

    def preference(workspace: Workspace) = Preference(workspace.persistentDir)
    def authenticationStore(workspace: Workspace) = AuthenticationStore(workspace.persistentDir)

    /**
     * Construct Service from user modifiable options
     *
     *  -> this method can be extended to add user defined services (e.g. change output redirection at this level ?)
     *
     * @param workspace workspace path
     * @param password password to be encrypted
     * @param httpProxy optional http proxy
     * @return
     */
    def apply(workspace: File, password: String, httpProxy: Option[String]) = {
      implicit val ws = Workspace(workspace)
      implicit val cypher = Cypher(password)
      implicit val preference = Services.preference(ws)
      implicit val newFile = NewFile(ws)
      implicit val seeder = Seeder()
      implicit val serializerService = SerializerService()
      implicit val threadProvider = ThreadProvider()
      implicit val replicaCatalog = ReplicaCatalog(ws)
      implicit val authenticationStore = Services.authenticationStore(ws)
      implicit val fileService = FileService()
      implicit val randomProvider = RandomProvider(seeder.newRNG)
      implicit val eventDispatcher = EventDispatcher()
      implicit val outputRedirection = OutputRedirection()
      implicit val networkService = NetworkService(httpProxy)
      implicit val fileServiceCache = FileServiceCache()

      new ServicesContainer()
    }

    /**
     * Dispose of services (deleted tmp directories ; stop the thread provider)
     * @param services
     * @return
     */
    def dispose(services: Services) = {
      util.Try(services.workspace.tmpDir.recursiveDelete)
      util.Try(services.threadProvider.stop())
    }

    /**
     * reset user password
     * @param authenticationStore
     * @param preference
     */
    def resetPassword(implicit authenticationStore: AuthenticationStore, preference: Preference) = {
      authenticationStore.delete()
      preference.clear()
    }

    def copy(services: Services)(
      workspace:           Workspace           = services.workspace,
      preference:          Preference          = services.preference,
      cypher:              Cypher              = services.cypher,
      threadProvider:      ThreadProvider      = services.threadProvider,
      seeder:              Seeder              = services.seeder,
      replicaCatalog:      ReplicaCatalog      = services.replicaCatalog,
      newFile:             NewFile             = services.newFile,
      authenticationStore: AuthenticationStore = services.authenticationStore,
      serializerService:   SerializerService   = services.serializerService,
      fileService:         FileService         = services.fileService,
      randomProvider:      RandomProvider      = services.randomProvider,
      eventDispatcher:     EventDispatcher     = services.eventDispatcher,
      outputRedirection:   OutputRedirection   = services.outputRedirection,
      networkService:      NetworkService      = services.networkService,
      fileServiceCache:    FileServiceCache    = services.fileServiceCache
    ) =
      new ServicesContainer()(
        workspace = workspace,
        preference = preference,
        cypher = cypher,
        threadProvider = threadProvider,
        seeder = seeder,
        replicaCatalog = replicaCatalog,
        newFile = newFile,
        authenticationStore = authenticationStore,
        serializerService = serializerService,
        fileService = fileService,
        fileServiceCache = fileServiceCache,
        randomProvider = randomProvider,
        eventDispatcher = eventDispatcher,
        outputRedirection = outputRedirection,
        networkService = networkService
      )

  }

  /**
   * Trait for services
   */
  trait Services {
    implicit def workspace: Workspace
    implicit def preference: Preference
    implicit def cypher: Cypher
    implicit def threadProvider: ThreadProvider
    implicit def seeder: Seeder
    implicit def replicaCatalog: ReplicaCatalog
    implicit def newFile: NewFile
    implicit def authenticationStore: AuthenticationStore
    implicit def serializerService: SerializerService
    implicit def fileService: FileService
    implicit def randomProvider: RandomProvider
    implicit def eventDispatcher: EventDispatcher
    implicit def outputRedirection: OutputRedirection
    implicit def networkService: NetworkService
    implicit def fileServiceCache: FileServiceCache
  }

  /**
   * A container for services, constructed with implicit arguments
   * @param workspace workspace
   * @param preference user preferences
   * @param cypher encrypter for password
   * @param threadProvider
   * @param seeder provides seed for rng
   * @param replicaCatalog replica database manager
   * @param newFile new files/dirs and tmp files / dir in a given directory
   * @param authenticationStore
   * @param serializerService serializer
   * @param fileService file management
   * @param randomProvider rng
   * @param eventDispatcher
   * @param outputRedirection
   * @param networkService network properties (proxies)
   * @param fileServiceCache
   */
  class ServicesContainer(implicit
    val workspace: Workspace,
                          val preference:          Preference,
                          val cypher:              Cypher,
                          val threadProvider:      ThreadProvider,
                          val seeder:              Seeder,
                          val replicaCatalog:      ReplicaCatalog,
                          val newFile:             NewFile,
                          val authenticationStore: AuthenticationStore,
                          val serializerService:   SerializerService,
                          val fileService:         FileService,
                          val randomProvider:      RandomProvider,
                          val eventDispatcher:     EventDispatcher,
                          val outputRedirection:   OutputRedirection,
                          val networkService:      NetworkService,
                          val fileServiceCache:    FileServiceCache) extends Services

}
