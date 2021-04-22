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
package org.openmole.plugin.domain.modifier

import org.openmole.core.dsl._
import org.openmole.core.dsl.extension._
import org.openmole.tool.random._

object ShuffleDomain {

  implicit def isDiscrete[D, T] = new DiscreteFromContextDomain[ShuffleDomain[D, T], T] with DomainInputs[ShuffleDomain[D, T]] {
    override def iterator(domain: ShuffleDomain[D, T]) = FromContext { p ⇒
      import p._
      domain.discrete.iterator(domain.domain).from(context).toSeq.shuffled(random()).iterator
    }
    override def inputs(domain: ShuffleDomain[D, T]) = domain.inputs.inputs(domain.domain)
  }

}

case class ShuffleDomain[D, +T](domain: D)(implicit val discrete: DiscreteFromContextDomain[D, T], val inputs: DomainInputs[D])