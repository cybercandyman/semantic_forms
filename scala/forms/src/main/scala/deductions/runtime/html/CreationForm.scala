package deductions.runtime.html

import scala.util.Try
import scala.xml.NodeSeq
import org.w3.banana.RDF
import deductions.runtime.abstract_syntax.UnfilledFormFactory
import deductions.runtime.dataset.RDFStoreLocalProvider
import deductions.runtime.sparql_cache.RDFCacheAlgo
import org.w3.banana.RDFOps
import deductions.runtime.services.Configuration
//import deductions.runtime.services.ConfigurationCopy
import deductions.runtime.utils.RDFPrefixes
import deductions.runtime.utils.I18NMessages
import deductions.runtime.utils.HTTPrequest
import play.api.libs.json.Json
import deductions.runtime.abstract_syntax.FormSyntaxJson

trait CreationFormAlgo[Rdf <: RDF, DATASET]
extends RDFCacheAlgo[Rdf, DATASET]
with UnfilledFormFactory[Rdf, DATASET]
with HTML5TypesTrait[Rdf]
with RDFPrefixes[Rdf]
with FormSyntaxJson[Rdf] {

  
  val config: Configuration

  import ops._
  import rdfStore.transactorSyntax._
  /** TODO also defined elsewhere */
  var actionURI = "/save"

  /**
   * create an XHTML input form for a new instance from a class URI;
   *  transactional TODO classUri should be an Option
   */
  def create(classUri: String, lang: String = "en",
    formSpecURI: String = "", graphURI: String= "", request: HTTPrequest= HTTPrequest() )
      : Try[NodeSeq] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    dataset.rw({
      val form = createData(classUri, lang, formSpecURI, graphURI, request)
      val ops1 = ops
      val config1 = config
      val htmlFormatter = new Form2HTMLBanana[Rdf]
      {
        val ops = ops1
        val config = config1
        val nullURI = URI("")
      }

      val rawForm = htmlFormatter . generateHTML(
          form, hrefPrefix = "",
          editable = true,
          actionURI = actionURI,
          lang=lang, graphURI=graphURI)

          Seq( makeEditingHeader(fromUri(uriNodeToURI(form.classs)), lang, formSpecURI, graphURI),
              rawForm ) . flatten
    })
  }

  def createData(classUri: String, lang: String = "en",
                 formSpecURI: String = "", graphURI: String = "",
                 request: HTTPrequest = HTTPrequest()) : FormSyntax = {
    val classURI = URI(classUri)
    retrieveURINoTransaction(classURI, dataset)
    preferedLanguage = lang
    implicit val graph: Rdf#Graph = allNamedGraph
    val form = createFormFromClass(classURI, formSpecURI, request)
    form
  }

  def createDataAsJSON(classUri: String, lang: String = "en",
                       formSpecURI: String = "", graphURI: String = "",
                       request: HTTPrequest = HTTPrequest()) = {
    val formSyntax = rdfStore.rw( dataset, {
      createData(classUri, lang, formSpecURI, graphURI, request)
    }) . get
    formSyntax2JSON(formSyntax)
  }

  def makeEditingHeader(classUri: String, lang: String,
                        formSpecURI: String, graphURI: String): NodeSeq = {
    <div class="sf-form-header">
      { I18NMessages.get("CREATING", lang) }
      { abbreviateTurtle(classUri) }
    </div>
  }

  /** create an XHTML input form for a new instance from a class URI; transactional */
  def createElem(uri: String, lang: String = "en")
  (implicit graph: Rdf#Graph)
  : NodeSeq = {
    //	  Await.result(
    create(uri, lang).getOrElse(
      <p>Problem occured when creating an XHTML input form from a class URI.</p>)
    //			  5 seconds )
  }

}
