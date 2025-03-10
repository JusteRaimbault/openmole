package org.openmole.core.csv

import java.io.PrintStream

import org.openmole.tool.stream.StringOutputStream
import org.scalatest._
import org.scalatest.junit._
import org.openmole.core.context._

class CSVSpec extends FlatSpec with Matchers {

  def result(f: PrintStream ⇒ Unit): String = {
    val result = new StringOutputStream()
    val printStream = new PrintStream(result)
    f(printStream)
    printStream.close()
    result.builder.toString
  }

  "Function" should "produce conform csv" in {
    assert(
      result(writeVariablesToCSV(_, None, Seq(42, 56, Array(89, 89)))) ===
        """42,56,"[89,89]"
          |""".stripMargin
    )

    assert(
      result(writeVariablesToCSV(_, None, Seq(42, 56, Array(89, 101)), unrollArray = true)) ===
        """42,56,89
        |42,56,101
        |""".stripMargin
    )
  }

  "ArrayOnRow" should "generate one column by element" in {
    val i = Val[Array[Int]]
    val j = Val[Array[Array[Int]]]
    val k = Val[String]

    def iValues = Array(89, 88, 72)
    def jValue = Array(Array(iValues, iValues), Array(65))

    def headerValue = header(Seq(i, j, k), Seq(iValues, jValue, "youpi"), arrayOnRow = true)

    assert(
      headerValue === "i$0,i$1,i$2,j$0$0$0,j$0$0$1,j$0$0$2,j$0$1$0,j$0$1$1,j$0$1$2,j$1$0,k"
    )

    assert(
      result(writeVariablesToCSV(_, Some(headerValue), Seq(iValues, jValue, "youpi"), arrayOnRow = true)) ==
        """i$0,i$1,i$2,j$0$0$0,j$0$0$1,j$0$0$2,j$0$1$0,j$0$1$1,j$0$1$2,j$1$0,k
        |89,88,72,89,88,72,89,88,72,65,youpi
        |""".stripMargin
    )

  }
}
