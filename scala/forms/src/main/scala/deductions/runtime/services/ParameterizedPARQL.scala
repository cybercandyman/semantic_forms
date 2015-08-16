package deductions.runtime.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.Elem
import org.w3.banana.RDF
import org.w3.banana.SparqlOpsModule
import org.w3.banana.TryW
import org.w3.banana.syntax._
import deductions.runtime.abstract_syntax.InstanceLabelsInference2
import deductions.runtime.dataset.RDFStoreLocalProvider
import deductions.runtime.html.Form2HTML
import deductions.runtime.abstract_syntax.PreferredLanguageLiteral
import org.w3.banana.Transactor
import org.w3.banana.RDFOpsModule
import org.w3.banana.RDFOps

trait SPARQLQueryMaker {
  def makeQueryString(search: String): String
}

/**
 * Generic SPARQL SELECT Search with single parameter,
 * and single return URI value,
 *  and showing in HTML a column of hyperlinked results with instance Labels
 */
trait ParameterizedSPARQL[Rdf <: RDF, DATASET]
    extends RDFStoreLocalProvider[Rdf, DATASET]
    with InstanceLabelsInference2[Rdf]
    with PreferredLanguageLiteral[Rdf] {
  //  self: RDFStoreLocalProvider[Rdf, DATASET] with InstanceLabelsInference2[Rdf] =>

  //	implicit val ops: RDFOps[Rdf]

  import ops._
  import sparqlOps._
  import rdfStore.transactorSyntax._
  import rdfStore.sparqlEngineSyntax._

  /** search and display results as an XHTML element */
  def search(search: String, hrefPrefix: String = "")(implicit queryMaker: SPARQLQueryMaker): Future[Elem] = {
    val uris = search_only(search)
    val elem = uris.map(
      u => displayResults(u.toIterable, hrefPrefix))
    elem
  }

  //  type N <: Rdf#Node
  /**
   * CAUTION: It is of particular importance to note that
   *  one should never use an iterator after calling a method on it;
   *
   *  TRANSACTIONAL
   */
  private def displayResults(res0: Iterable[Rdf#Node], hrefPrefix: String) = {
    <p>{
      val res = res0.toSeq
      println(s"displayResults : ${res.mkString("\n")}")
      dataset.r({
        val graph: Rdf#Graph = allNamedGraph
        //        res.sortBy(x => instanceLabel(x)).
        res.sortBy(uri => instanceLabel(uri, graph, "" /* TODO lang*/ )).
          map(uri => {
            val uriString = uri.toString
            val blanknode = !isURI(uri)
            // TODO : show named graph
            <div title={ uri.toString() }>
              <a href={ Form2HTML.createHyperlinkString(hrefPrefix, uriString, blanknode) }>
                {
                  instanceLabel(uri, graph, "")
                }
              </a><br/>
            </div>
          })
      }).get
    }</p>
  }

  /**
   * NOTE: this stuff is pretty generic;
   *  just add these arguments :
   *  queryString:String, vars:Seq[String]
   *  ; TRANSACTIONAL
   */
  private def search_only(search: String)(implicit queryMaker: SPARQLQueryMaker): Future[Iterator[Rdf#Node]] = {
    val queryString = queryMaker.makeQueryString(search)

    val transaction =
      dataset.r({
        val result = for {
          query <- parseSelect(queryString)
          solutions <- dataset.executeSelect(query, Map())
        } yield {
          solutions.toIterable.map {
            row =>
              row("thing") getOrElse sys.error(s"search_only($search) : no ?thing in row")
          }
        }
        result
      })
    val tryIteratorRdfNode = transaction.flatMap { identity }
    tryIteratorRdfNode.asFuture
  }

  def isURI(node: Rdf#Node) = ops.foldNode(node)(identity, x => None, x => None) != None

}