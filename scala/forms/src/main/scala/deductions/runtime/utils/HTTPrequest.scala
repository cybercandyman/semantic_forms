package deductions.runtime.utils

/**
 * Like Request from Play! (in package play.api.mvc), but avoid Play! dependency
 *  [[play.api.mvc.Request]]
 */
case class HTTPrequest(
    /**
     * The HTTP host (domain, optionally port)
     */
    host: String = "",
    /**
     * The client IP address.
     *
     * the last untrusted proxy
     * from the Forwarded-Headers or the X-Forwarded-*-Headers.
     */
    remoteAddress: String = "",

    rawQueryString: String = "",

    /** YET UNUSED */
    headers: Map[String, Seq[String]] = Map()) {

  def absoluteURL(rawURI: String = "", secure: Boolean = false): String =
    "http" + (if (secure) "s" else "") + "://" +
      this.host + rawURI // + this.appendFragment
}
