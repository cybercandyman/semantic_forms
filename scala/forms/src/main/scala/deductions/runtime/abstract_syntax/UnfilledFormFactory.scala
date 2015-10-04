/**
 *
 */

package deductions.runtime.abstract_syntax

import scala.util.Try
import org.w3.banana.RDF
import org.w3.banana.RDFOps
import org.w3.banana.RDFStore
import org.w3.banana.URIOps
//import UnfilledFormFactory.defaultInstanceURIPrefix
import org.w3.banana.SparqlGraphModule
import org.w3.banana.SparqlEngine
import org.w3.banana.SparqlOps
import scala.language.postfixOps
import deductions.runtime.services.Configuration

/**
 * @author j.m. Vanel
 *
 */
object UnfilledFormFactory {
  /** make a unique Id with given prefix, currentTimeMillis() and nanoTime() */
  def makeId(instanceURIPrefix: String): String = {
    instanceURIPrefix + System.currentTimeMillis() + "-" + System.nanoTime() // currentId = currentId + 1
  }
}

/** Factory for an Unfilled Form */
//abstract class
trait UnfilledFormFactory[Rdf <: RDF, DATASET]
//(graph: Rdf#Graph,
//  preferedLanguage: String = "en",
//  instanceURIPrefix: String = defaultInstanceURIPrefix)
//  (implicit ops: RDFOps[Rdf],
//    uriOps: URIOps[Rdf],
//    rdfStore: RDFStore[Rdf, Try, DATASET],
//    sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph],
//    sparqlOps: SparqlOps[Rdf])
    extends FormSyntaxFactory[Rdf, DATASET]
   	with FormConfigurationFactory[Rdf]
    with Configuration {

//  val graph: Rdf#Graph
//  val preferedLanguage: String = "en"
  val instanceURIPrefix: String = defaultInstanceURIPrefix
  
//  import formConfiguration._
  import ops._

  /**
   * create Form from a class URI,
   *  looking up for Form Configuration within RDF graph in this class
   */
  def createFormFromClass(classs: Rdf#URI,
    formSpecURI: String = "")
  	  (implicit graph: Rdf#Graph)
  	  : FormModule[Rdf#Node, Rdf#URI]#FormSyntax = {
	  val formConfiguration = this // new FormConfigurationFactory[Rdf](graph)
//	  import formConfiguration._

    val (propsListInFormConfig, formConfig) =
      if (formSpecURI != "") {
        (propertiesListFromFormConfiguration(URI(formSpecURI)),
          URI(formSpecURI))
      } else {
        lookPropertieslistFormInConfiguration(classs)
      }
    val newId = makeId
    if (propsListInFormConfig.isEmpty) {
      val props = fieldsFromClass(classs, graph)
      createFormDetailed(makeUri(newId), props toSeq, classs, CreationMode)
    } else
      createFormDetailed(makeUri(newId), propsListInFormConfig.toSeq, classs,
        CreationMode, formConfig = formConfig)
  }

  def makeId: String = {
    UnfilledFormFactory.makeId(instanceURIPrefix)
  }
}