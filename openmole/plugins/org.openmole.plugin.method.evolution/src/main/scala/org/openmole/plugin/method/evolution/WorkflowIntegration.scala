/*
 * Copyright (C) 2014 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
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

package org.openmole.plugin.method.evolution

import org.openmole.core.dsl._
import org.openmole.core.dsl.extension._

import scala.language.higherKinds

import cats._
import cats.implicits._

import org.openmole.core.exception.UserBadDataError

case class Stochastic(
  seed:       SeedVariable = SeedVariable.empty,
  sample:     Int          = 100,
  reevaluate: Double       = 0.2
)

object WorkflowIntegration {

  def stochasticity(objectives: Objectives, stochastic: Option[Stochastic]) =
    (Objective.onlyExact(objectives), stochastic) match {
      case (true, None)     ⇒ None
      case (true, Some(s))  ⇒ Some(s)
      case (false, Some(s)) ⇒ Some(s)
      case (false, None)    ⇒ throw new UserBadDataError("Aggregation have been specified for some objective, but no stochastic parameter is provided.")
    }

  implicit def hlistContainingIntegration[H <: shapeless.HList, U](implicit hwi: WorkflowIntegrationSelector[H, U]) = new WorkflowIntegration[H] {
    def apply(h: H) = hwi.selected(hwi(h))
  }

  def deterministicGAIntegration[AG](a: DeterministicGA[AG]): EvolutionWorkflow =
    new EvolutionWorkflow {
      type MGOAG = AG
      def mgoAG = a.ag

      type V = (Vector[Double], Vector[Int])
      type P = Array[Any]

      lazy val integration = a.algorithm

      def buildIndividual(genome: G, context: Context): I =
        operations.buildIndividual(genome, variablesToPhenotype(context), context)

      def inputPrototypes = Genome.toVals(a.genome)
      def outputPrototypes = a.objectives.map(Objective.prototype)
      def resultPrototypes = (inputPrototypes ++ outputPrototypes).distinct

      def genomeToVariables(genome: G): FromContext[Vector[Variable[_]]] = {
        val (cs, is) = operations.genomeValues(genome)
        Genome.toVariables(a.genome, cs, is, scale = true)
      }

      def variablesToPhenotype(context: Context) = a.objectives.map(o ⇒ Objective.prototype(o)).map(context.apply(_)).toArray
      //def variablesToPhenotype(context: Context) = a.objectives.map(o ⇒ Objective.toDouble(o, context)).toVector
    }

  def stochasticGAIntegration[AG](a: StochasticGA[AG]): EvolutionWorkflow =
    new EvolutionWorkflow {
      type MGOAG = AG
      def mgoAG = a.ag

      type V = (Vector[Double], Vector[Int])
      type P = Array[Any]

      lazy val integration = a.algorithm

      def buildIndividual(genome: G, context: Context): I =
        operations.buildIndividual(genome, variablesToPhenotype(context), context)

      def inputPrototypes = Genome.toVals(a.genome) ++ a.replication.seed.prototype
      def outputPrototypes = a.objectives.map(Objective.prototype)

      def genomeToVariables(genome: G): FromContext[Seq[Variable[_]]] = {
        val (continuous, discrete) = operations.genomeValues(genome)
        val seeder = a.replication.seed
        (Genome.toVariables(a.genome, continuous, discrete, scale = true) map2 FromContext { p ⇒ seeder(p.random()) })(_ ++ _)
      }

      def variablesToPhenotype(context: Context) = a.objectives.map(o ⇒ Objective.prototype(o)).map(context.apply(_)).toArray
    }

  case class DeterministicGA[AG](
    ag:         AG,
    genome:     Genome,
    objectives: Seq[ExactObjective[_]]
  )(implicit val algorithm: MGOAPI.Integration[AG, (Vector[Double], Vector[Int]), Array[Any]])

  object DeterministicGA {
    implicit def deterministicGAIntegration[AG]: WorkflowIntegration[DeterministicGA[AG]] = new WorkflowIntegration[DeterministicGA[AG]] {
      def apply(a: DeterministicGA[AG]) = WorkflowIntegration.deterministicGAIntegration(a)
    }

    def toEvolutionWorkflow(a: DeterministicGA[_]): EvolutionWorkflow = WorkflowIntegration.deterministicGAIntegration(a)
  }

  case class StochasticGA[AG](
    ag:          AG,
    genome:      Genome,
    objectives:  Seq[NoisyObjective[_]],
    replication: Stochastic
  )(
    implicit
    val algorithm: MGOAPI.Integration[AG, (Vector[Double], Vector[Int]), Array[Any]]
  )

  object StochasticGA {
    implicit def stochasticGAIntegration[AG, P]: WorkflowIntegration[StochasticGA[AG]] = new WorkflowIntegration[StochasticGA[AG]] {
      override def apply(a: StochasticGA[AG]) = WorkflowIntegration.stochasticGAIntegration(a)
    }

    def toEvolutionWorkflow(a: StochasticGA[_]): EvolutionWorkflow = WorkflowIntegration.stochasticGAIntegration(a)
  }

  def apply[T](f: T ⇒ EvolutionWorkflow) = new WorkflowIntegration[T] {
    def apply(t: T) = f(t)
  }

}

trait WorkflowIntegration[T] {
  def apply(t: T): EvolutionWorkflow
}

object EvolutionWorkflow {
  implicit def isWorkflowIntegration: WorkflowIntegration[EvolutionWorkflow] = new WorkflowIntegration[EvolutionWorkflow] {
    def apply(t: EvolutionWorkflow) = t
  }

  //case class EvolutionState[S](s: S, island)
}

trait EvolutionWorkflow {

  type MGOAG
  def mgoAG: MGOAG

  val integration: MGOAPI.Integration[MGOAG, V, P]
  import integration._

  def operations = integration.operations(mgoAG)

  type G = integration.G
  type I = integration.I
  type S = integration.S

  type V
  type P

  type Pop = Array[I]

  def genomeType = ValType[G]
  def stateType = ValType[S]
  def individualType = ValType[I]

  def populationType: ValType[Pop] = ValType[Pop]

  def buildIndividual(genome: G, context: Context): I

  def inputPrototypes: Seq[Val[_]]
  def outputPrototypes: Seq[Val[_]]

  def genomeToVariables(genome: G): FromContext[Seq[Variable[_]]]

  // Variables
  import GAIntegration.namespace

  def genomePrototype = Val[G]("genome", namespace)(genomeType)
  def individualPrototype = Val[I]("individual", namespace)(individualType)
  def populationPrototype = Val[Pop]("population", namespace)(populationType)
  def offspringPrototype = Val[Pop]("offspring", namespace)(populationType)
  def statePrototype = Val[S]("state", namespace)(stateType)
  def generationPrototype = Val[Long]("generation", namespace)
  def terminatedPrototype = Val[Boolean]("terminated", namespace)
}

object GAIntegration {

  def namespace = Namespace("evolution")
  def samples = Val[Int]("samples", namespace)

  def genomeToVariable(
    genome: Genome,
    values: (Vector[Double], Vector[Int]),
    scale:  Boolean) = {
    val (continuous, discrete) = values
    Genome.toVariables(genome, continuous, discrete, scale)
  }

  def genomesOfPopulationToVariables[I](
    genome: Genome,
    values: Vector[(Vector[Double], Vector[Int])],
    scale:  Boolean): FromContext[Vector[Variable[_]]] = {

    val variables =
      FromContext { p ⇒
        import p._
        values.map {
          case (continuous, discrete) ⇒ Genome.toVariables(genome, continuous, discrete, scale).from(context)
        }
      }

    variables.map {
      v ⇒
        genome.zipWithIndex.map {
          case (g, i) ⇒ Genome.toArrayVariable(g, v.map(_(i).value))
        }.toVector
    }
  }

  def objectivesOfPopulationToVariables[I](objectives: Seq[Objective[_]], phenotypeValues: Vector[Vector[Double]]): FromContext[Vector[Variable[_]]] =
    objectives.toVector.zipWithIndex.map {
      case (objective, i) ⇒
        Variable(
          Objective.resultPrototype(objective).withType[Array[Double]],
          phenotypeValues.map(_(i)).toArray
        )
    }

  def rejectValue[G](reject: Condition, genome: Genome, continuous: G ⇒ Vector[Double], discrete: G ⇒ Vector[Int]) = FromContext { p ⇒ (g: G) ⇒
    import p._
    val genomeVariables = GAIntegration.genomeToVariable(genome, (continuous(g), discrete(g)), scale = true).from(context)
    reject.from(genomeVariables)
  }

}

object DeterministicGAIntegration {
  def migrateToIsland[P](population: Vector[mgo.evolution.algorithm.CDGenome.DeterministicIndividual.Individual[P]]) = population
  def migrateFromIsland[P](population: Vector[mgo.evolution.algorithm.CDGenome.DeterministicIndividual.Individual[P]]) = population
}

object StochasticGAIntegration {

  def migrateToIsland[I](population: Vector[I], historyAge: monocle.Lens[I, Long]) = population.map(historyAge.set(0))
  def migrateFromIsland[I, P](population: Vector[I], historyAge: monocle.Lens[I, Long], history: monocle.Lens[I, Array[P]]) = {
    def keepIslandHistoryPart(i: I) = history.modify(h ⇒ h.takeRight(historyAge.get(i).toInt))(i)
    population.filter(i ⇒ historyAge.get(i) > 0).map(keepIslandHistoryPart)
  }

}

object MGOAPI {

  trait Integration[A, V, P] {
    type I
    type G
    type S

    implicit def iManifest: Manifest[I]
    implicit def gManifest: Manifest[G]
    implicit def sManifest: Manifest[S]

    def operations(a: A): Ops

    trait Ops {
      def metadata(generation: Long, frequency: Option[Long]): FromContext[EvolutionMetadata] = FromContext { _ ⇒ EvolutionMetadata.none }

      def initialState: S
      def initialGenomes(n: Int, rng: scala.util.Random): FromContext[Vector[G]]

      def buildIndividual(genome: G, phenotype: P, context: Context): I

      def genomeValues(genome: G): V
      def buildGenome(values: V): G
      def buildGenome(context: Vector[Variable[_]]): FromContext[G]

      def startTimeLens: monocle.Lens[S, Long]
      def generationLens: monocle.Lens[S, Long]

      def breeding(individuals: Vector[I], n: Int, s: S, rng: scala.util.Random): FromContext[Vector[G]]
      def elitism(population: Vector[I], candidates: Vector[I], s: S, rng: scala.util.Random): FromContext[(S, Vector[I])]

      def migrateToIsland(i: Vector[I]): Vector[I]
      def migrateFromIsland(population: Vector[I], state: S): Vector[I]

      def afterGeneration(g: Long, s: S, population: Vector[I]): Boolean
      def afterDuration(d: squants.Time, s: S, population: Vector[I]): Boolean

      def result(population: Vector[I], state: S): FromContext[Seq[Variable[_]]]
    }

  }

  import mgo.evolution.algorithm._

  def paired[G, C, D](continuous: G ⇒ C, discrete: G ⇒ D) = (g: G) ⇒ (continuous(g), discrete(g))

}

