package deductions.runtime.jena

import deductions.runtime.data_cleaning.BlankNodeCleanerBatch
import deductions.runtime.services.DefaultConfiguration

object BlankNodeCleanerApp extends {
  override val config = new DefaultConfiguration {
    override val useTextQuery = false
  }
} with RDFStoreLocalJena1Provider
    with App
    with BlankNodeCleanerBatch[ImplementationSettings.Rdf, ImplementationSettings.DATASET] {
  //  val config = new DefaultConfiguration {
  //    override val useTextQuery = false
  //  }
  cleanUnreachableBlankNodeSubGraph()
}