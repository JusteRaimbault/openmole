/*
 * Copyright (C) 2011 <mathieu.Mathieu Leclaire at openmole.org>
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openmole.ide.plugin.prototype.base

import org.openmole.core.implementation.data._
import org.openmole.core.model.data._

class DoublePrototypeDataUI(val name: String = "", d: Int = 0) extends GenericPrototypeDataUI[Double](d) {
  def displayTypedName = toString + " : Double"

  def coreClass = classOf[Prototype[Double]]

  def coreObject = Prototype[Double](name).toArray(dim).asInstanceOf[Prototype[Double]]

  def fatImagePath = "img/double_fat.png"

  def buildPanelUI = new DoublePrototypePanelUI(this)
}
