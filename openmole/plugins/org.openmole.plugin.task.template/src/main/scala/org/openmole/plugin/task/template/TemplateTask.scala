/*
 * Copyright (C) 2015 Romain Reuillon
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
package org.openmole.plugin.task.template

import org.openmole.core.workflow.builder._
import org.openmole.core.workflow.data._
import org.openmole.core.workflow.task._
import org.openmole.core.workflow.dsl
import dsl._
import java.io.File

import monocle.macros.Lenses
import org.openmole.core.workflow.tools._
import org.openmole.core.workspace._

object TemplateTask {

  implicit def isBuilder = TaskBuilder[TemplateTask].from(this)

  def apply(
    template: String,
    output:   Prototype[File]
  ) = new TemplateTask(template, output) set (dsl.outputs += output)

}

@Lenses case class TemplateTask(
    template: String,
    output:   Prototype[File],
    inputs:   PrototypeSet    = PrototypeSet.empty,
    outputs:  PrototypeSet    = PrototypeSet.empty,
    defaults: DefaultSet      = DefaultSet.empty,
    name:     Option[String]  = None
) extends Task {

  @transient lazy val expanded = VariableExpansion(template)

  override def process(context: Context, executionContext: TaskExecutionContext)(implicit rng: RandomProvider) = {
    val outputFile = executionContext.tmpDirectory.newFile("output", "template")
    outputFile.content = expanded.expand(context)
    Context.empty + (output, outputFile)
  }
}
