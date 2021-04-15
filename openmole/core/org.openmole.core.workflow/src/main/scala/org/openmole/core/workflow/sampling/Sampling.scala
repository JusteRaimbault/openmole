/*
 * Copyright (C) 2010 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openmole.core.workflow.sampling

import org.openmole.core.context._
import org.openmole.core.expansion._

object Sampling {

  /**
   * API constructor for FromContextSampling
   * @param samples
   * @return
   */
  def apply(samples: FromContext.Parameters ⇒ Iterator[Iterable[Variable[_]]]) = FromContextSampling(samples)

}

trait Sampling {

  /**
   * Prototypes of the variables required by this sampling.
   *
   * @return the data
   */
  def inputs: PrototypeSet = PrototypeSet.empty

  /**
   * Prototypes of the factors generated by this sampling.
   *
   * @return the factors prototypes
   */
  def prototypes: Iterable[Val[_]]

  /**
   * This method builds the explored plan in the given {@code context}.
   */
  @throws(classOf[Throwable])
  def apply(): FromContext[Iterator[Iterable[Variable[_]]]]

}
