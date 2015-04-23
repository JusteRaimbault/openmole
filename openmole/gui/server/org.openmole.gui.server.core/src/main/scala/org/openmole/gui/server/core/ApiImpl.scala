package org.openmole.gui.server.core

import org.openmole.core.workspace.Workspace
import org.openmole.gui.shared._
import org.openmole.gui.ext.data.TreeNodeData
import java.io.File

/*
 * Copyright (C) 21/07/14 // mathieu.leclaire@openmole.org
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object ApiImpl extends Api {

  def listFiles(tnd: TreeNodeData): Seq[TreeNodeData] = Utils.listFiles(tnd.canonicalPath)

  def addRootDirectory(name: String): Boolean = new File(Utils.workspaceProjectFile, name).mkdir

  def workspacePath(): String = Utils.workspaceProjectFile.getCanonicalPath()
}
