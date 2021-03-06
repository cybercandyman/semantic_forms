package deductions.runtime.utils

import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Character.toUpperCase
import java.util.StringTokenizer
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps
import org.apache.any23.vocab.CSV
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.w3.banana.FOAFPrefix
import org.w3.banana.RDF
import org.w3.banana.RDFOps
import org.w3.banana.RDFPrefix
import org.w3.banana.RDFSPrefix
import org.w3.banana.XSDPrefix
import deductions.runtime.services.Configuration
import org.w3.banana.PrefixBuilder

/** made from CSVExtractor from Any23;
 *  TODO: probably should be in another SBT project */
trait CSVImporter[Rdf <: RDF, DATASET]
		extends 
//		Configuration
//		with 
		URIHelpers
		with RDFPrefixes[Rdf] {

  val config: Configuration
  import config._

  implicit val ops: RDFOps[Rdf]
  import ops._
  
  private val rdf = RDFPrefix[Rdf]
//  private val rdfs = RDFSPrefix[Rdf]
  private val xsd = XSDPrefix[Rdf]

  /** lots of boiler plate for vocabularies !!!!!!!!!!! */
  object CCOPrefix {
    def apply[Rdf <: RDF: RDFOps](implicit ops: RDFOps[Rdf]) = new CCOPrefix(ops)
  }
  class CCOPrefix[Rdf <: RDF](ops: RDFOps[Rdf])
      extends PrefixBuilder("cco", "http://purl.org/ontology/cco/core#")(ops) {
    val expertise = apply("expertise")
  }
  private val cco = CCOPrefix[Rdf]
  
  object AVPrefix {
    def apply[Rdf <: RDF: RDFOps](implicit ops: RDFOps[Rdf]) = new AVPrefix(ops)
  }
  class AVPrefix[Rdf <: RDF](ops: RDFOps[Rdf])
      extends PrefixBuilder("av", prefixAVontology )(ops) {
      val idea = apply("idea")
      val contributesToOrganization = apply("contributesToOrganization")
      val metAt = apply("metAt")
  }
  private val av = AVPrefix[Rdf]

  object VCardPrefix {
    def apply[Rdf <: RDF: RDFOps](implicit ops: RDFOps[Rdf]) = new VCardPrefix(ops)
  }
  class VCardPrefix[Rdf <: RDF](ops: RDFOps[Rdf])
      extends PrefixBuilder("vcard", "http://www.w3.org/2006/vcard/ns#" )(ops) {
	  val postal_code = apply("postal-code")
	  val locality =  apply("locality")
  }
  private val vcard = VCardPrefix[Rdf]

  ///////////////////////////////////
  
  private var csvParser: CSVParser = _
  type URI = Rdf#URI

  private var headerURIs: IndexedSeq[URI] = _

  private var csv: CSV = CSV.getInstance

  /** TODO consider using R2RML vocab' */
  private def csvPredicate(p: String) = URI(CSV.NS + p)

  /** run the parser and "semantize"
   *  @param documentURI document URI to be predended to column names */
  def run(
      in: InputStream,
      documentURI: URI,
      /* property Value pair to add For Each Row */
      propertyValueForEachRow: List[(Rdf#URI, Rdf#Node)] = List() ): Rdf#Graph = {
    
    val rowType = csvPredicate(CSV.ROW_TYPE)
    
    csvParser = new CSVParser( new InputStreamReader(in) , CSVFormat.DEFAULT .withHeader() )
    val header: java.util.Map[String, Integer] = csvParser.getHeaderMap
    headerURIs = processHeader(header, documentURI)
    
    val list = ArrayBuffer[Rdf#Triple]()
    
    writeHeaderPropertiesMetadata(header, list)
    var index = 0
    val rowSubjectPrefix = {
      val doc = documentURI.toString
      if( doc.endsWith("/") ||
        doc.endsWith("#") ) doc + "row/"
      else
        doc + "/row/"
    }
    for( record <- csvParser.getRecords ) {
      val rowSubject = URI( rowSubjectPrefix + index)
      list += Triple(rowSubject, rdf.typ, rowType)
      produceRowStatements(rowSubject, record, list)
      for( pv <- propertyValueForEachRow ) {
    	  list += Triple(rowSubject, pv._1, pv._2)
      }
      list += Triple(documentURI, csvPredicate(CSV.ROW), rowSubject)
      list += Triple(rowSubject, csvPredicate(CSV.ROW_POSITION), Literal( String.valueOf(index) ) )
      index = index + 1
    }
    addTableMetadataStatements(documentURI, list, index, headerURIs.length)
    makeGraph(list)
  }

  private def isInteger(number: String): Boolean = {
    try {
      java.lang.Integer.valueOf(number)
      true
    } catch {
      case e: NumberFormatException => false
    }
  }

  private def isFloat(number: String): Boolean = {
    try {
      java.lang.Float.valueOf(number)
      true
    } catch {
      case e: NumberFormatException => false
    }
  }

  private def writeHeaderPropertiesMetadata(
    header: java.util.Map[String, Integer],
    list: ArrayBuffer[Rdf#Triple]) {
    var index = 0
    for (singleHeader <- headerURIs) {
      if (index <= headerURIs.length) {
        if (!isAbsoluteURI(fromUri(singleHeader))) {
          list += Triple(singleHeader, rdfs.label, Literal(fromUri(singleHeader)))
        }
        list += Triple(singleHeader, csvPredicate(CSV.COLUMN_POSITION), Literal(String.valueOf(index), xsd.integer))
        index = index + 1
      }
    }
  }

  private def processHeader(header:  java.util.Map[String, Integer],
      documentURI: URI): ArrayBuffer[URI] = {
    val result = ArrayBuffer.fill( header.size )(URI(""))
    var index = 0
    for (h <- header.keys) {
      val candidate = h.trim()
      val headerURI =
        recognizePrefixedURI(candidate) match {
        case Some(uri) => uri
        case None => manageColumnsMapping(candidate, documentURI)
      }
      result . update( index, headerURI )
      index += 1
    }
    result
  }

  def recognizePrefixedURI(candidate: String): Option[Rdf#URI] = {
    if (isAbsoluteURI(candidate) ||
        candidate.startsWith(":") ) {
      // accept prefixed URI's like foaf:name, and expand them
      expand(candidate) match {
        case Some(uri) => Some(uri)
        case None      => Some(URI(candidate))
      }
    } else None
  }

  /** columns Mappings from Guillaume's column names to well-known RDF properties
   *  TODO
   *  make this Map an argument
   *  use labels on properties to propose properties to user,
   *  manage prefixes globally, maybe using prefix.cc */
  val columnsMappings = Map(
      // foaf:Person
      "Prénom" -> foaf.givenName,
      "Nom" -> foaf.familyName,
      "organisation 1" -> av.contributesToOrganization,
      "organisation 2" -> av.contributesToOrganization,

      // foaf:Organization or foaf:Person     
      "Email" -> foaf.mbox,
      "Téléphone" -> foaf.phone,
      "Catégorie" -> foaf.focus,
      "Code postal" -> vcard.postal_code,
	    "Ville" -> vcard.locality,
	    "Description courte" -> foaf.name,
	    "Description Longue" -> rdfs.comment,
	    "Site Web" -> foaf.homepage,
	    
      "Projet 1" -> foaf.currentProject,
      "Projet 2" -> foaf.currentProject,
      "Projet 3" -> foaf.currentProject,
      
      "compétence 1" -> cco.expertise,
      "compétence 2" -> cco.expertise,
      "autre" -> cco.expertise,
      
      "Idée 1" -> av.idea,
      "Idée 2" -> av.idea,
      "Rencontré à" -> av.metAt,

      // GV
      
      /*	",SUIVI,SITE WEB,PRINT SIGNA,
      Nom structure pour admnistration,Nom pour communication,
      Prénom interlocuteur référent,Nom interlocuteur référent,Numéro contact,Adresse e-mail,
      Prénom interlocuteur référent 2,Nom interlocuteur référent 2,Numéro contact 2,Adresse e-mail 2,
      Activité,Site Web,Bâtiment,Espace,Arrivée,Contribution au projet proposée,Contribution réalisée,
      Nombre salariés,
      Page Facebook,Compte twitter,NB de mentions presses,Ventes ou services,Structure juridique signature convention,
      Numéro de clé au PC
      http://dbpedia.org/ontology/longName
      */
      "Nom structure pour admnistration" ->  foaf.name,
      "Nom pour communication" -> URI("http://dbpedia.org/ontology/longName"),
      "Prénom interlocuteur référent" -> foaf.givenName,
      "Nom interlocuteur référent" -> foaf.familyName,
      "Numéro contact" -> foaf.phone,
      "Adresse e-mail" -> foaf.mbox,
      "Page Facebook" -> av("facebook"),
      "Compte twitter" -> av("twitter"),

      // Tasks management

      "Tâche" ->  rdfs.label,
      "Etat" ->  tasks("state"),
      "JH estimé" ->  tasks("workDurationEstimated"),
      "JH réel" ->  tasks("workDuration"),
      "Assignée à" ->  tasks("assignee"),
      "Suivi par" ->  tasks("managedBy"),
      "Réalisée par" ->  tasks("realizedBy"),
      "TJM" ->  tasks(""),  // ???
      "Tarif estimé" ->  tasks("estimatedPrice"),  // ???
      "Tarif réel" ->  tasks("price"),
      "Détail" ->  rdfs.comment,
      "Discussion" ->  tasks("discussion"),
      "Groupe" ->  tasks("groupe"),  // ???
      "Priorité" ->  tasks("priority"),  // ???
      "Technologie" ->  tasks("technology"),
      "Difficulté" ->  tasks("hardness"),  // ???

      // ONISEP

      /* For DuplicateCleanerSpecificationApp; NOTE: any rdf:type can be replaced,
      it's not necessarily properties */
      "Identifiant de la propriété" -> URI(restruc.prefixIri + "property"),
      "Id" -> URI(restruc.prefixIri + "property"),
      "Action" -> URI(restruc.prefixIri + "replacingProperty"),

          "Nouveau libellé" -> rdfs.label,
          "Propriété à renommer" -> rdfs.label,
          "Commentaire fusion" -> rdfs.comment
  )

  /** manage Mapping from Column name to URI */
  private def manageColumnsMapping(columnName: String, documentURI: URI): URI = {
    println(s"manageColumnsMapping: columnName '$columnName': <${columnsMappings.getOrElse( columnName, normalize( columnName, documentURI) )}>")
    columnsMappings.getOrElse( columnName.trim(),
        normalize( columnName, documentURI) )
  }

  /** normalize column names: remove "&", "?", "/", capitalize;
   * prepend document URI */
  private def normalize(toBeNormalized0: String, documentURI: URI): URI = {
    val toBeNormalized = toBeNormalized0.trim().toLowerCase().
    replace("?", "").
    replace("/", "").
    replace("&", "")
    val result = new StringBuilder(documentURI.toString)
    val tokenizer = new StringTokenizer(toBeNormalized, " ")
    while (tokenizer.hasMoreTokens()) {
      val current = tokenizer.nextToken()
      result.append(toUpperCase(current.charAt(0))).append(current.substring(1))
    }
    URI(result.toString)
  }

  private def produceRowStatements(
    rowSubject: URI,
    record: CSVRecord,
    list: ArrayBuffer[Rdf#Triple]) {
    val values = record.iterator()
    var index = 0
    for (cell <- values) {
      if (index < headerURIs.length) {
        if (cell != "") {
          val predicate = headerURIs(index)
          val `object` = getObjectFromCell(cell)
          list += Triple(rowSubject, predicate, `object`)
        }
        index += 1
      }
    }
  }

  /** get RDF Object From Cell */
  private def getObjectFromCell(cell0: String): Rdf#Node = {
    val cell = cell0.trim()
    if (isAbsoluteURI(cell)) {
        URI(cell)

    } else if( cell.contains(":")) {
    	recognizePrefixedURI(cell) match {
        case Some(uri) => uri
        case None => Literal(cell)
      }
      
    } else {

      var datatype =
        if (isInteger(cell)) {
          xsd.integer
        } else if (isFloat(cell)) {
          xsd.float
        } else
          xsd.string        
      Literal(cell, datatype)
    }
  }
  
  private def addTableMetadataStatements(documentURI: URI, 
		  list: ArrayBuffer[Rdf#Triple],
		  numberOfRows: Int, 
      numberOfColumns: Int) {
    list += Triple(documentURI, csvPredicate(CSV.NUMBER_OF_ROWS), Literal(String.valueOf(numberOfRows), xsd.integer))
    list += Triple(documentURI, csvPredicate(CSV.NUMBER_OF_COLUMNS), Literal(String.valueOf(numberOfColumns), 
      xsd.integer))
  }

}
