package org.openmole.core.workflow.grouping

import org.openmole.core.context.Context
import org.openmole.core.workflow.job.MoleJob
import org.openmole.core.workflow.mole.{ MoleJobGroup, NewGroup }
import org.openmole.tool.random.RandomProvider

trait Grouping {
  def apply(context: Context, groups: Iterable[(MoleJobGroup, Iterable[MoleJob])])(implicit newGroup: NewGroup, randomProvider: RandomProvider): MoleJobGroup

  def complete(job: Iterable[MoleJob]) = false
}
