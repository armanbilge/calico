package calico.html

object codecs:
  // String Codecs

  object StringAsIsCodec extends AsIsCodec[String]

  // Int Codecs

  object IntAsIsCodec extends AsIsCodec[Int]

  object IntAsStringCodec extends Codec[Int, String] {
    override def decode(domValue: String): Int = domValue.toInt // @TODO this can throw exception. How do we handle this?
    override def encode(scalaValue: Int): String = scalaValue.toString
  }

  // Double Codecs

  object DoubleAsIsCodec extends AsIsCodec[Double]

  object DoubleAsStringCodec extends Codec[Double, String] {
    override def decode(domValue: String): Double = domValue.toDouble// @TODO this can throw exception. How do we handle this?
    override def encode(scalaValue: Double): String = scalaValue.toString
  }

  // Boolean Codecs

  object BooleanAsIsCodec extends AsIsCodec[Boolean]

  object BooleanAsAttrPresenceCodec extends Codec[Boolean, String] {
    override def decode(domValue: String): Boolean = domValue != null
    override def encode(scalaValue: Boolean): String = if scalaValue then "" else null
  }

  object BooleanAsTrueFalseStringCodec extends Codec[Boolean, String] {
    override def decode(domValue: String): Boolean = domValue == "true"
    override def encode(scalaValue: Boolean): String = if scalaValue then "true" else "false"
  }

  object BooleanAsYesNoStringCodec extends Codec[Boolean, String] {
    override def decode(domValue: String): Boolean = domValue == "yes"
    override def encode(scalaValue: Boolean): String = if scalaValue then "yes" else "no"
  }

  object BooleanAsOnOffStringCodec extends Codec[Boolean, String] {
    override def decode(domValue: String): Boolean = domValue == "on"
    override def encode(scalaValue: Boolean): String = if scalaValue then "on" else "off"
  }

  // Iterable Codecs

  object IterableAsSpaceSeparatedStringCodec extends Codec[Iterable[String], String] { // use for e.g. className
    override def decode(domValue: String): Iterable[String] = if domValue == "" then Nil else domValue.split(' ')
    override def encode(scalaValue: Iterable[String]): String = scalaValue.mkString(" ")
  }

  object IterableAsCommaSeparatedStringCodec extends Codec[Iterable[String], String] { // use for lists of IDs
    override def decode(domValue: String): Iterable[String] = if domValue == "" then Nil else domValue.split(',')
    override def encode(scalaValue: Iterable[String]): String = scalaValue.mkString(",")
  }

  /** Use this codec when you don't need any data transformation */

  trait AsIsCodec[T] extends Codec[T, T] {
    override def decode(domValue: T): T = domValue
    override def encode(scalaValue: T): T = scalaValue
  }

  object AsIsCodec {

    /** Note: We already have several AsIsCodec instances in codecs/package.scala */
    def apply[T]: AsIsCodec[T] = new AsIsCodec[T] {}
  }

  /** This trait represents a way to encode and decode HTML attribute or DOM property values.
    *
    * It is needed because attributes encode all values as strings regardless of their type,
    * and then there are also multiple ways to encode e.g. boolean values. Some attributes
    * encode those as "true" / "false" strings, others as presence or absence of the element,
    * and yet others use "yes" / "no" or "on" / "off" strings, and properties encode booleans
    * as actual booleans.
    *
    * Scala DOM Types hides all this mess from you using codecs. All those pseudo-boolean
    * attributes would be simply `Attr[Boolean](name, codec)` in your code.
    * */
  trait Codec[ScalaType, DomType] {

    /** Convert the result of a `dom.Node.getAttribute` call to appropriate Scala type.
      *
      * Note: HTML Attributes are generally optional, and `dom.Node.getAttribute` will return
      * `null` if an attribute is not defined on a given DOM node. However, this decoder is
      * only intended for cases when the attribute is defined.
      */
    def decode(domValue: DomType): ScalaType

    /** Convert desired attribute value to appropriate DOM type. The resulting value should
      * be passed to `dom.Node.setAttribute` call, EXCEPT when resulting value is a `null`.
      * In that case you should call `dom.Node.removeAttribute` instead.
      *
      * We use `null` instead of [[Option]] here to reduce overhead in JS land. This method
      * should not be called by end users anyway, it's the consuming library's job to
      * call this method under the hood.
      */
    def encode(scalaValue: ScalaType): DomType
  }

  object Codec {
    private[calico] val whitespaceSeparatedStringsCodec: Codec[List[String], String] = new:
      def decode(domValue: String) = domValue.split(" ").toList

      def encode(scalaValue: List[String]) =
        if scalaValue.isEmpty then ""
        else
          var acc = scalaValue.head
          var tail = scalaValue.tail
          while tail.nonEmpty do
            acc += " " + tail.head
            tail = tail.tail
          acc
  }
