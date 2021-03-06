package deductions.runtime.html

import java.net.URLEncoder
import java.security.MessageDigest

import scala.Range
import scala.xml.Elem
import scala.xml.NodeSeq
import scala.xml.NodeSeq.seqToNodeSeq
import scala.xml.Text
import scala.xml.Unparsed
import Form2HTML.urlEncode
import deductions.runtime.abstract_syntax.DBPediaLookup
import deductions.runtime.abstract_syntax.FormModule
import deductions.runtime.utils.I18NMessages
import deductions.runtime.utils.Timer
import deductions.runtime.services.Configuration
import org.apache.commons.codec.digest.DigestUtils
import deductions.runtime.abstract_syntax.FormModule

/**
 * different modes: display or edit;
 *  takes in account datatype
 */
private [html] trait Form2HTML[NODE, URI <: NODE]
    extends Form2HTMLDisplay[NODE, URI]
    with Form2HTMLEdit[NODE, URI]
    with FormModule[NODE, URI]
    with Timer
    with JavaScript
    {
  self: HTML5Types =>

import config._

  /**
   * render the given Form Syntax as HTML;
   *  @param hrefPrefix URL prefix pre-pended to created ID's for Hyperlink
   *  @param actionURI, actionURI2 HTML actions for the 2 submit buttons
   *  @param graphURI URI for named graph to save user inputs
   */
  def generateHTML(form: FormModule[NODE, URI]#FormSyntax,
                   hrefPrefix: String = "",
                   editable: Boolean = false,
                   actionURI: String = "/save", graphURI: String = "",
                   actionURI2: String = "/save", lang: String = "en"): NodeSeq = {

    val htmlFormFields = time("generateHTMLJustFields",
      generateHTMLJustFields(form, hrefPrefix, editable, graphURI, lang))

    def wrapFieldsWithFormHeader(htmlFormFields: NodeSeq): NodeSeq =
      <div class="container">  
      	<div class="row">
					<form action={ actionURI } method="POST">
        		<p class="text-right">
          		<input value={ mess("SAVE") } type="submit" class="btn btn-primary btn-lg"/>
        		</p>
            { htmlFormFields }
        		<p class="text-right">
          		<input value={ mess("SAVE") } type="submit" formaction={ actionURI2 } class="btn btn-primary btn-lg pull-right"/>
        		</p>
      		</form>
				</div>
			</div>
    def mess(m: String): String = message(m, lang)

    if (editable)
      wrapFieldsWithFormHeader(htmlFormFields)
    else
      htmlFormFields
  }

  /**
   * generate HTML, but Just Fields;
   *  this lets application developers create their own submit button(s) and <form> tag
   */
  def generateHTMLJustFields(form: formMod#FormSyntax,
                             hrefPrefix: String = "",
                             editable: Boolean = false,
                             graphURI: String = "", lang: String = "en"): NodeSeq = {

    implicit val formImpl: formMod#FormSyntax = form

    val hidden: NodeSeq = if (editable) {
      <input type="hidden" name="url" value={ urlEncode(form.subject) }/>
      <input type="hidden" name="graphURI" value={ urlEncode(graphURI) }/>
    } else Seq()

    def makeFieldsLabelAndData(fields: Seq[FormEntry]): NodeSeq = {
      if (!fields.isEmpty) {
        val lastEntry = fields.last
        val s: Seq[Elem] = for (
          (preceding, field) <- (lastEntry +: fields) zip fields // do not display NullResourceEntry
          if (field.property.toString != "")
        ) yield {
          <div class={ css.cssClasses.formLabelAndInputCSSClass }>{
            makeFieldSubject(field) ++
            makeFieldLabel(preceding, field) ++
            makeFieldDataOrInput(field, hrefPrefix, editable, lang)
          }</div>
        }
        s
      } else Text("\n")
    }

   /**
     * makeFieldsGroups Builds a groups of HTML fields to be used with the jQuery UI tabs generator
     * 
     * @return NodeSeq Fragment HTML contenant un groupe de champs
     */
    def makeFieldsGroups(): NodeSeq = {
      val map = form.propertiesGroups

      def makeHref(s: String) = DigestUtils.md5Hex(s)

      // http://jqueryui.com/accordion/ or http://jqueryui.com/tabs/
      val tabsNames = <ul>{
        for( pgs <- map) yield {
          val label = pgs.title
          <li><a href={ "#" + makeHref(label) }>{ label }</a></li>
        }
      }</ul>

      val r = for (pgs <- map) yield {
        val label = pgs.title
        println(s"Fields Group $label")
        Seq(
          <div class="sf-fields-group"  id={  makeHref(label) } >,
           <div class="sf-fields-group-title">{ label }</div>,
            { makeFieldsLabelAndData(pgs.fields) }
          </div>)
      }
      val tabs: Seq[Elem] = r.flatten.toSeq
      tabs . +: (tabsNames)
    }

    def makeFieldSubject(field: FormEntry): NodeSeq = {
      if (field.subject != nullURI && field.subject != form.subject) {
        val subjectField =
          // NOTE: over-use of class ResourceEntry to display the subject instead of normally the object triple:
          ResourceEntry(value=field.subject, valueLabel=field.subjectLabel)
        createHTMLField(subjectField, editable, hrefPrefix, lang)
      } else NodeSeq.Empty
    }

    val htmlResult: NodeSeq =
      hidden ++
      <div class={ css.cssClasses.formRootCSSClass }>
        {
          css.localCSS ++
            Text("\n") ++
            (if (inlineJavascriptInForm)
              localJS
            else NodeSeq.Empty) ++
              Text("\n") ++
              <input type="hidden" name="uri" value={ urlEncode(form.subject) }/> ++
              <div>
                {
                  Text(form.title) ++
                    (if (form.subject != nullURI)
                      Text(", at URI ") ++ <a href={ toPlainString(form.subject) }>&lt;{ form.subject }&gt;</a>
                    else NodeSeq.Empty)
                }
              </div> ++
            {
              if (groupFields) {
                makeFieldsGroups()
              } else
                makeFieldsLabelAndData(form.fields)
            }
        }
      </div>
    return htmlResult
  }

  /** dispatch to various Entry's: LiteralEntry, ResourceEntry; ..., editable or not */
  private def createHTMLField(field: formMod#Entry, editable: Boolean,
    hrefPrefix: String = "", lang: String = "en")(implicit form: FormModule[NODE, URI]#FormSyntax): xml.NodeSeq = {
    
    // hack instead of true form separator in the form spec in RDF:
    if (field.label.contains("----"))
      return <hr style="background:#F87431; border:0; height:4px"/> // Text("----")

    val xmlField = field match {
      case l: formMod#LiteralEntry =>
          if (editable)
            createHTMLiteralEditableField(l)
          else
            createHTMLiteralReadonlyField(l)
            
      case r: formMod#ResourceEntry =>
        /* link to a known resource of the right type,
           * or (TODO) create a sub-form for a blank node of an ancillary type (like a street address),
           * or just create a new resource with its type, given by range, or derived
           * (like in N3Form in EulerGUI ) */
          if (editable)
            createHTMLResourceEditableField(r, lang)
          else
            createHTMLResourceReadonlyField(r, hrefPrefix)

      case r: formMod#BlankNodeEntry =>
          if (editable)
            createHTMLBlankNodeEditableField(r)
          else
            createHTMLBlankNodeReadonlyField(r, hrefPrefix)

      case r: formMod#RDFListEntry => <p>RDF List: {
        r.values.fields.map {
          field => field.valueLabel
        }.
          mkString(", ")
      }</p>

      case _ => <p>Should not happen! createHTMLField({ field })</p>
    }

    Seq(createAddRemoveWidgets(field, editable)) ++ xmlField
  }

  /** make Field Data (display) Or Input (edit) */
  private def makeFieldDataOrInput(field: formMod#Entry, hrefPrefix: String,
    editable: Boolean, lang: String = "en")(implicit form: FormModule[NODE, URI]#FormSyntax) = {
    if (shouldAddAddRemoveWidgets(field, editable))
      createHTMLField(field, editable, hrefPrefix, lang)
    else if (editable)
      // that's for corporate_risk:
       <div class={ css.cssClasses.formInputCSSClass }>
         { createHTMLField(field, editable, hrefPrefix, lang) }
       </div>
    else
       createHTMLField(field, editable, hrefPrefix, lang)
  }

}

object Form2HTML {
  def urlEncode(node: Any) = URLEncoder.encode(node.toString, "utf-8")

  def createHyperlinkString(hrefPrefix: String, uri: String, blanknode: Boolean = false): String = {
    if (hrefPrefix == "")
      uri
    else {
      val suffix = if (blanknode) "&blanknode=true" else ""
      hrefPrefix + urlEncode(uri) + suffix
    }
  }
}
