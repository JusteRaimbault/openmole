/*
 * Copyright (C) 2021 Romain Reuillon
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

package org.openmole.plugin.domain.modifier

import org.openmole.core.dsl._
import org.openmole.core.dsl.extension._

object DistinctDomain {

  implicit def isDiscrete[D, T]: DiscreteFromContextDomain[DistinctDomain[D, T], T] =
    domain ⇒
      FromContext { p ⇒
        import p._
        domain.discrete.iterator(domain.domain).from(context).toSeq.distinct.iterator
      }

  implicit def inputs[D, T](implicit inputs: DomainInput[D]): DomainInput[DistinctDomain[D, T]] = domain ⇒ inputs(domain.domain)
  implicit def validate[D, T](implicit validate: DomainValidation[D]): DomainValidation[DistinctDomain[D, T]] = domain ⇒ validate(domain.domain)

}

case class DistinctDomain[D, +T](domain: D)(implicit val discrete: DiscreteFromContextDomain[D, T])