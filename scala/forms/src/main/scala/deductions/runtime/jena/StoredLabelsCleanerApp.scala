package deductions.runtime.jena

import deductions.runtime.abstract_syntax.InstanceLabelsInferenceMemory
import deductions.runtime.services.DefaultConfiguration

/** How to enumerate the languages used by all users? */
object StoredLabelsCleanerApp extends {
  override val config = new DefaultConfiguration {
    override val useTextQuery = false
  }
} with RDFStoreLocalJena1Provider
    with App
    with InstanceLabelsInferenceMemory[ImplementationSettings.Rdf, ImplementationSettings.DATASET] {

  import config._
  //  close()
  for (lang <- List("en", "fr", "")) {
    cleanStoredLabels(lang)
  }
  close()
}