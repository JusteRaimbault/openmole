package org.openmole.core.pluginregistry

import java.util.concurrent.ConcurrentHashMap

import org.openmole.core.highlight._
import org.openmole.core.namespace._
import org.openmole.core.preference._

import scala.collection.JavaConverters._

object PluginRegistry {

  val plugins = new ConcurrentHashMap[AnyRef, PluginInfo]().asScala

  def addPlugin(c: AnyRef, info: PluginInfo) = plugins += c → info
  def removePlugin(c: AnyRef) = plugins -= c
  def pluginsInfo = plugins.values

  def register(
    id:                 AnyRef,
    nameSpaces:         Vector[ExportedNameSpace]      = Vector(),
    nameSpaceTraits:    Vector[ExportedNameSpaceTrait] = Vector(),
    highLight:          Vector[HighLight]              = Vector(),
    preferenceLocation: Vector[PreferenceLocation[_]]  = Vector()): Unit = {
    val info = PluginInfo(nameSpaces, nameSpaceTraits, highLight, preferenceLocation)
    PluginRegistry.addPlugin(id, info)
  }

  def unregister(id: AnyRef): Unit = PluginRegistry.removePlugin(id)

  def highLights = plugins.values.flatMap(_.highLights)

}

case class PluginInfo(
  namespaces:          Vector[ExportedNameSpace],
  namespaceTraits:     Vector[ExportedNameSpaceTrait],
  highLights:          Vector[HighLight],
  preferenceLocations: Vector[PreferenceLocation[_]])