package deductions.runtime.services

import scala.concurrent.Future
import scala.util.Try

import org.w3.banana.RDF
import org.w3.banana.io.RDFWriter
import org.w3.banana.io.Turtle

import deductions.runtime.dataset.RDFStoreLocalProvider

/**
 * Browsable Graph implementation, in the sense of
 *  http://www.w3.org/DesignIssues/LinkedData.html
 *
 *  (used for Turtle export)
 */
trait BrowsableGraph[Rdf <: RDF, DATASET] extends RDFStoreLocalProvider[Rdf, DATASET]
    with SPARQLHelpers[Rdf, DATASET] {

  val turtleWriter: RDFWriter[Rdf, Try, Turtle]

  import ops._
  import sparqlOps._
  import rdfStore.sparqlEngineSyntax._
  import rdfStore.transactorSyntax._

  /**
   * used in Play! app;
   * NON transactional
   * 
   * @param search : subject or object URI
   * @return
   * all triples <search> ?p ?o   ,
   * plus optionally all triples in graph <search> , plus "reverse" triples everywhere
   */
  def search_only(search: String): Try[Rdf#Graph] = {
    val queryString =
      s"""
         |CONSTRUCT {
         |  <$search> ?p ?o .
         |  ?thing ?p ?o .
         |  ?s ?p1 <$search> .     
         |}
         |WHERE {
         |  GRAPH ?GRAPH
         |  { <$search> ?p ?o . }
         |  OPTIONAL {
         |    GRAPH <$search>
         |    { ?thing ?p ?o . }
         |    GRAPH ?GRAPH2
         |    { ?s ?p1 <$search> . } # "reverse" triples
         |  }
         |}""".stripMargin
    println("search_only " + queryString)
    val res = sparqlConstructQuery(queryString)
    println( "search_only: after sparqlConstructQuery" )
    res
  }

  /** used in Play! app , but blocking ! transactional */
  def focusOnURI(uri: String, mime: String="text/turtle"): String = {
    try {
      val transaction = dataset.r({
        val triples = search_only(uri)
        triples
      })
      // TODO RDF/XML
      val format = if( mime.contains("turtle")) "turtle" else "jsonld"

      graph2String(transaction.get, uri, format=format)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }
  }

}
