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

package org.openmole.plugin.environment.egi

import org.openmole.core.tools.service.MovingAverage
import org.openmole.core.tools.service._
import org.openmole.tool.logger.Logger

object QualityControl extends Logger

import QualityControl.Log._

trait QualityControl {
  def hysteresis: Int

  def isEmpty = _successRate.isEmpty || operationTime.isEmpty || _availability.isEmpty

  private lazy val _successRate = new MovingAverage(hysteresis)
  private lazy val operationTime = new MovingAverage(hysteresis)
  private lazy val _availability = new MovingAverage(hysteresis)

  def wasAvailable = _availability.put(1.0)
  def wasNotAvailable = _availability.put(0.0)

  def failed = _successRate.put(0)
  def success = _successRate.put(1)
  def successRate = _successRate.get
  def timed(t: Double) = operationTime.put(t)

  def time = operationTime.get
  def availability = _availability.get

  def quality[A](op: ⇒ A): A = timed {
    try {
      val ret = op
      success
      ret
    }
    catch {
      case e: Throwable ⇒
        failed
        throw e
    }
  }

  def timed[A](op: ⇒ A): A = {
    val begin = System.currentTimeMillis
    val a = op
    timed(System.currentTimeMillis - begin)
    a
  }

}
