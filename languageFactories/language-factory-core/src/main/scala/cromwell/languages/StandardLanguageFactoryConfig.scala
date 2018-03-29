package cromwell.languages

import common.validation.Checked._
import common.Checked

final case class StandardLanguageFactoryConfig(validateAllInputs: Boolean, enabled: Boolean, unusedKeys: Map[String, Any]) {
  def checkForUnwantedInputs = validateAllInputs
  def enabledCheck: Checked[Unit] = if (enabled) {
    ().validNelCheck
  } else {
    "WDL draft 3 is not enabled".invalidNelCheck
  }
}

object StandardLanguageFactoryConfig {
  def parse(values: Map[String, Any], allowExtras: Boolean): StandardLanguageFactoryConfig = {

    def parseFold(current: StandardLanguageFactoryConfig, next: (String, Any)) = next match {
      case ("validate-all-inputs", b: Boolean) => current.copy(validateAllInputs = b)
      case ("enabled", b: Boolean) => current.copy(enabled = b)
      case other if allowExtras => current.copy(unusedKeys = current.unusedKeys + other)
      case other => throw new RuntimeException(s"Non-standard configuration value for language factory: $other")
    }

    val initial = StandardLanguageFactoryConfig(validateAllInputs = true, enabled = true, Map.empty)
    values.foldLeft(initial)(parseFold)
  }
}
