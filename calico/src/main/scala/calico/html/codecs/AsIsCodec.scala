package calico.html.codecs

/**
 * Use this codec when you don't need any data transformation
 */

trait AsIsCodec[T] extends Codec[T, T] {
  override def decode(domValue: T): T = domValue
  override def encode(scalaValue: T): T = scalaValue
}

object AsIsCodec {

  /**
   * Note: We already have several AsIsCodec instances in codecs/package.scala
   */
  def apply[T]: AsIsCodec[T] = new AsIsCodec[T] {}

  object BooleanAsIsCodec extends AsIsCodec[Boolean]

  object DoubleAsIsCodec extends AsIsCodec[Double]

  object StringAsIsCodec extends AsIsCodec[String]

  // Int Codecs

  object IntAsIsCodec extends AsIsCodec[Int]
}
