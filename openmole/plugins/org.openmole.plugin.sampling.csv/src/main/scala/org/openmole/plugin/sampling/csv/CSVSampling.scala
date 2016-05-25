/*
 * Copyright (C) 2011 mathieu
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

package org.openmole.plugin.sampling.csv

import java.io.File

import monocle.Lens
import monocle.macros.Lenses
import org.openmole.core.workflow.builder.SamplingBuilder
import org.openmole.core.workflow.data.{ DefaultSet, Prototype, PrototypeSet }
import org.openmole.core.workflow.sampling._
import org.openmole.core.workflow.tools._
import org.openmole.plugin.tool.csv.{ CSVToVariables, CSVToVariablesBuilder }

object CSVSampling {

  implicit def isBuilder = new CSVToVariablesBuilder[CSVSampling] with SamplingBuilder[CSVSampling] {
    override def columns = CSVSampling.columns
    override def fileColumns = CSVSampling.fileColumns
    override def separator = CSVSampling.separator
    override def outputs = CSVSampling.outputs
    override def inputs = CSVSampling.inputs
    override def defaults = CSVSampling.defaults
  }

  def apply(file: FromContext[File]): CSVSampling =
    new CSVSampling(
      file,
      inputs = PrototypeSet.empty,
      outputs = PrototypeSet.empty,
      defaults = DefaultSet.empty,
      name = None,
      columns = Vector.empty,
      fileColumns = Vector.empty,
      separator = None
    )

  def apply(file: File): CSVSampling = apply(file)
  def apply(directory: File, name: ExpandedString): CSVSampling = apply(FileList(directory, name))

}

@Lenses case class CSVSampling(
    file:                FromContext[File],
    override val inputs: PrototypeSet,
    outputs:             PrototypeSet,
    defaults:            DefaultSet,
    name:                Option[String],
    columns:             Vector[(String, Prototype[_])],
    fileColumns:         Vector[(String, File, Prototype[File])],
    separator:           Option[Char]
) extends Sampling with CSVToVariables {

  override def prototypes = outputs
  //    columns.map { case (_, p) ⇒ p } :::
  //      fileColumns.map { case (_, _, p) ⇒ p } ::: Nil

  override def apply() = FromContext { (context, rng) ⇒ toVariables(file.from(context)(rng), context) }

}