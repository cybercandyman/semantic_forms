package deductions.runtime.html

import java.nio.file.Files
import java.nio.file.Paths

import org.apache.log4j.Logger
import org.junit.Assert
import org.scalatest.FunSuite

import deductions.runtime.abstract_syntax.FormSyntaxFactoryTest
import deductions.runtime.jena.ImplementationSettings
import deductions.runtime.jena.RDFStoreLocalJena1Provider
import deductions.runtime.services.DefaultConfiguration

//class Form2HTMLTestJena extends Form2HTMLTest

class Form2HTMLTest
    extends FunSuite
    with RDFStoreLocalJena1Provider
    with FormSyntaxFactoryTest[ImplementationSettings.Rdf, ImplementationSettings.DATASET] //    with DefaultConfiguration
    {
  val config = new DefaultConfiguration {
    override val useTextQuery = false
  }
  val logger = Logger.getRootLogger()

  import ops._
  println("Entering Form2HTMLTest")
  val nullURI1 = makeUri("")

  test("Test HTML") {
    val ops1 = ops
    val fh = new Form2HTMLBanana[Rdf] with DefaultConfiguration {
      val ops = ops1
      val config = new DefaultConfiguration {}
      val nullURI = URI("")
    }
    val xhtml = fh.generateHTML(createFormWithGivenProps())
    Files.write(Paths.get("tmp.form.html"), xhtml.toString().getBytes);
    Assert.assertTrue(xhtml.toString().contains("Alexandre"))
  }
}
