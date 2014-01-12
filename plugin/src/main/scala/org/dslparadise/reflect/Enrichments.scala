package org.dslparadise
package reflect

import scala.language.implicitConversions
import scala.tools.nsc.{Global => NscGlobal}
import scala.tools.nsc.{Settings => NscSettings}
import org.dslparadise.{Settings => ParadiseSettings}

trait Enrichments {
  val global: NscGlobal
  implicit def paradiseSettings(settings: NscSettings) = ParadiseSettings
  def installationFailure() = global.abort("failed to install dslparadise plugin")
}
