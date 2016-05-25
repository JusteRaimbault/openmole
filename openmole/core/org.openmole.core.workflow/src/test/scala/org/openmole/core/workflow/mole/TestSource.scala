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
package org.openmole.core.workflow.mole

import monocle.macros.Lenses
import org.openmole.core.workflow.data.{ Context, DefaultSet, PrototypeSet, RandomProvider }

object TestSource {

  implicit def isBuilder = new SourceBuilder[TestSource] {
    override def defaults = TestSource.defaults
    override def inputs = TestSource.inputs
    override def name = TestSource.name
    override def outputs = TestSource.outputs
  }

}

@Lenses case class TestSource(
    f:        Context ⇒ Context = identity[Context],
    inputs:   PrototypeSet      = PrototypeSet.empty,
    outputs:  PrototypeSet      = PrototypeSet.empty,
    defaults: DefaultSet        = DefaultSet.empty,
    name:     Option[String]    = None
) extends Source {
  override protected def process(context: Context, executionContext: MoleExecutionContext)(implicit rng: RandomProvider): Context =
    f(context)
}