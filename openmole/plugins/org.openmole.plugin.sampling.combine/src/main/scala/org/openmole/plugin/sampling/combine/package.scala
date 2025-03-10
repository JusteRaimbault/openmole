/*
 * Copyright (C) 2012 Romain Reuillon
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

package org.openmole.plugin.sampling

import org.openmole.core.context.Val
import org.openmole.core.expansion.FromContext
import org.openmole.core.workflow.domain._
import org.openmole.core.workflow.dsl._
import org.openmole.core.workflow.sampling._
import org.openmole.plugin.domain.collection._
import org.openmole.plugin.domain.modifier._
import org.openmole.core.expansion._
import cats.implicits._

package object combine {

  trait AbstractSamplingCombineDecorator {
    def s: Sampling
    @deprecated("Use x instead", "5")
    def +(s2: Sampling) = x(s2)
    def x(s2: Sampling) = new CompleteSampling(s, s2)
    def ::(s2: Sampling) = new ConcatenateSampling(s, s2)
    def zip(s2: Sampling) = ZipSampling(s, s2)
    @deprecated("Use withIndex", "5")
    def zipWithIndex(index: Val[Int]) = withIndex(index)
    def withIndex(index: Val[Int]) = ZipWithIndexSampling(s, index)
    def sample(n: FromContext[Int]) = SampleSampling(s, n)
    def repeat(n: FromContext[Int]) = RepeatSampling(s, n)
    def bootstrap(samples: FromContext[Int], number: FromContext[Int]) = s sample samples repeat number
  }

  implicit class SamplingCombineDecorator(val s: Sampling) extends AbstractSamplingCombineDecorator {
    def shuffle = ShuffleSampling(s)
    def filter(keep: Condition) = FilteredSampling(s, keep)
    def take(n: FromContext[Int]) = TakeSampling(s, n)
    def subset(n: Int, size: FromContext[Int] = 100) = SubsetSampling(s, n, size = size)
    def drop(n: Int) = DropSampling(s, n)
  }

  implicit class DiscreteFactorDecorator[D, T](factor: Factor[D, T])(implicit discrete: Discrete[D, T]) extends AbstractSamplingCombineDecorator {
    def s: Sampling = FactorSampling(factor)
  }

  implicit def withNameFactorDecorator[D, T: CanGetName](factor: Factor[D, T])(implicit discrete: Discrete[D, T]) = new {
    @deprecated("Use withName", "5")
    def zipWithName(name: Val[String]): ZipWithNameSampling[D, T] = withName(name)
    def withName(name: Val[String]): ZipWithNameSampling[D, T] = new ZipWithNameSampling(factor, name)
  }

  implicit class TupleToZipSampling[T1, T2](ps: (Val[T1], Val[T2])) {
    def in[D](d: D)(implicit discrete: Discrete[D, (T1, T2)]) = {
      val d1 = discrete.iterator(d).map(_.map(_._1))
      val d2 = discrete.iterator(d).map(_.map(_._2))
      ZipSampling(ps._1 in d1, ps._2 in d2)
    }
  }

}