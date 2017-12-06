package dslparadise
package typechecker

import scala.collection.mutable.LinkedHashMap

trait ReportingSilencer {
  self: Analyzer =>

  import global._

  trait Silencer {
    val warningFields = try {
      val allWarningsField = currentRun.reporting.getClass.getDeclaredField("_allConditionalWarnings")
      allWarningsField.setAccessible(true)
      allWarningsField.get(currentRun.reporting).asInstanceOf[List[_]].headOption map { warning =>
        val warningsField = warning.getClass.getDeclaredField("warnings")
        warningsField.setAccessible(true)
        val doReportField = warning.getClass.getDeclaredField("doReport")
        doReportField.setAccessible(true)

        val yeasField = settings.warnUnused.getClass.getDeclaredField("yeas")
        yeasField.setAccessible(true)
        val naysField = settings.warnUnused.getClass.getDeclaredField("nays")
        naysField.setAccessible(true)
        val compute = settings.warnUnused.getClass.getDeclaredMethod("compute")
        compute.setAccessible(true)

        val unusedWarnings = settings.getClass.getMethod("UnusedWarnings").invoke(settings)
        val imports = unusedWarnings.getClass.getMethod("Imports").invoke(unusedWarnings)

        val yeas = yeasField.get(settings.warnUnused)
        val plus = yeas.getClass.getMethod("$" + "plus", classOf[Object])
        val minus = yeas.getClass.getMethod("$" + "minus", classOf[Object])

        (allWarningsField, warningsField, doReportField,
         yeasField, naysField, compute,
         imports, plus, minus)
      }
    }
    catch {
      case _: NoSuchFieldException => None
      case _: NoSuchMethodException => None
    }

    def silenceReporting[T](op: => T): T = warningFields match {
      case Some((
          allWarningsField, warningsField, doReportField,
          yeasField, naysField, compute,
          imports, plus, minus)) =>

        val allWarnings =
          allWarningsField.get(currentRun.reporting).asInstanceOf[List[_]]
        val warningBackups = allWarnings map warningsField.get
        allWarnings foreach {
          warningsField.set(_, LinkedHashMap.empty[Position, (String, String)])
        }
        val doReportBackups = allWarnings map doReportField.get
        allWarnings foreach {
          doReportField.set(_, () => false)
        }

        val yeasBackup = yeasField.get(settings.warnUnused)
        yeasField.set(settings.warnUnused, minus.invoke(yeasBackup, imports))
        val naysBackup = naysField.get(settings.warnUnused)
        naysField.set(settings.warnUnused, plus.invoke(naysBackup, imports))
        compute.invoke(settings.warnUnused)

        val result = op

        yeasField.set(settings.warnUnused, yeasBackup)
        naysField.set(settings.warnUnused, naysBackup)
        compute.invoke(settings.warnUnused)

        allWarnings zip warningBackups foreach (warningsField.set _).tupled
        allWarnings zip doReportBackups foreach (doReportField.set _).tupled

        result

      case None =>
        op
    }
  }
}
