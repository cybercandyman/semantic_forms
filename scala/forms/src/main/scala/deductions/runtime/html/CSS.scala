package deductions.runtime.html

/** CSS for forms */
trait CSS {

  lazy val cssClasses = tableCSSClasses

  val cssRules = """
      input.form-control[type='text']{ margin-bottom: 15px; }
      .form-value{ display: table-cell; width: 500px;
                   border-collapse: separate;
                   border-width: 10px; }
      .sf-external-link{color: DarkRed; background-color: lightyellow;}
      .sf-internal-link{}
      .sf-sparql-table td { padding:0px 10px; }
      .sf-sparql-table tr:first-of-type { font-weight:bold; }
"""

  lazy val localCSS = <style type='text/css'>{ cssRules }</style>

  /** default is here */
  case class CSSClasses(
    val formRootCSSClass: String = "form",
    val formFieldCSSClass: String = "form-group",
    val formLabelAndInputCSSClass: String = "row",
    val formLabelCSSClass: String = "control-label",
    val formDivInputCSSClass: String = "col-xs-9",
    val formInputCSSClass: String = "input",
    val formDivEditInputCSSClass: String = "col-xs-1",
    val formAddDivCSSClass: String = "col-xs-1",
    val formSelectDivCSSClass: String = "col-xs-1",
    val formSelectCSSClass: String = "form-select")

  /** actually applied CSS classes are here */
  lazy val tableCSSClasses = CSSClasses(
    formRootCSSClass = "form-horizontal",
    formFieldCSSClass = "",
    formLabelAndInputCSSClass = "form-group",
    formLabelCSSClass = "control-label col-xs-1",
    formDivInputCSSClass = "col-xs-9",
    formInputCSSClass = "form-control",
    formDivEditInputCSSClass = "col-xs-1",
    formAddDivCSSClass = "col-xs-1",
    formSelectDivCSSClass = "col-xs-1",
    formSelectCSSClass = "form-select")

  /** TODO use the LDP prefix; distinguish 1) dbpedia URI's 2) other external links 3) internal links */
  def cssForURI(uri: String) = {
    // if (uri.startsWith("http://dbpedia.org/resource/"))
    if (uri.contains("dbpedia.org/resource/"))
      "sf-external-link"
    else
      "sf-internal-link"
  }
}
