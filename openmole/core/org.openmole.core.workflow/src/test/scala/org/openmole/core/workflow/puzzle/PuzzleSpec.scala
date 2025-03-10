package org.openmole.core.workflow.puzzle

import org.openmole.core.context.Val
import org.openmole.core.workflow.dsl._
import org.openmole.core.workflow.execution.LocalEnvironment
import org.openmole.core.workflow.mole._
import org.openmole.core.workflow.task._
import org.openmole.core.workflow.test.TestHook
import org.openmole.core.workflow.validation.Validation
import org.scalatest._

class PuzzleSpec extends FlatSpec with Matchers {
  import org.openmole.core.workflow.test.Stubs._

  "A single task" should "be a valid mole" in {
    val t = EmptyTask()
    t.run()
  }

  "HList containing dsl container" should "be usable like a dsl container" in {
    import shapeless._

    val task = EmptyTask()
    val test = DSLContainer(task) :: 9 :: HNil

    (test: DSLContainer[_]).run()
    (test: MoleExecution).run()
    test.run()
    test on LocalEnvironment(1)
  }

  "Strain" should "pass a val through a single of task" in {
    @volatile var lastExecuted = false

    val i = Val[Int]

    val first = EmptyTask() set (outputs += i, i := 1)

    val last = FromContextTask("last") { p ⇒
      import p._
      context(i) should equal(1)
      lastExecuted = true
      context
    } set (inputs += i)

    val mole = first -- Strain(EmptyTask()) -- last
    mole run

    lastExecuted should equal(true)
  }

  "Strain" should "pass a val through a sequence of tasks" in {
    @volatile var lastExecuted = false

    val i = Val[Int]

    val first = EmptyTask() set (outputs += i, i := 1)

    val last = FromContextTask("last") { p ⇒
      import p._
      context(i) should equal(1)
      lastExecuted = true
      context
    } set (inputs += i)

    val mole = first -- Strain(EmptyTask() -- EmptyTask()) -- last
    mole run

    lastExecuted should equal(true)
  }

  "outputs method" should "return the dsl outputs" in {
    val i = Val[Int]
    val j = Val[String]

    val t = EmptyTask() set (outputs += (i, j))

    val o = (EmptyTask() -- t).outputs.toSet

    o.contains(i) should equal(true)
    o.contains(j) should equal(true)
  }

  "DSL container" should "be hookable" in {
    @volatile var hookExecuted = false

    val i = Val[Int]

    val first = EmptyTask() set (outputs += i, i := 1)
    val last = EmptyTask()

    val container = DSLContainer(first, output = Some(first))

    val h = TestHook { context ⇒ hookExecuted = true }

    (container hook h) run ()

    hookExecuted should equal(true)
  }

  "DSL" should "be compatible with script generation" in {
    def dsl(i: Int): DSL = EmptyTask()

    val wf = EmptyTask() -- (0 until 2).map(dsl)

    Validation(wf).isEmpty should be(true)
  }

}
