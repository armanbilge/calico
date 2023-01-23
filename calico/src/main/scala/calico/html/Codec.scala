/*
 * Copyright 2022 Arman Bilge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package calico.html

import scala.scalajs.js

/**
 * This trait represents a way to encode and decode HTML attribute or DOM property values.
 *
 * It is needed because attributes encode all values as strings regardless of their type, and
 * then there are also multiple ways to encode e.g. boolean values. Some attributes encode those
 * as "true" / "false" strings, others as presence or absence of the element, and yet others use
 * "yes" / "no" or "on" / "off" strings, and properties encode booleans as actual booleans.
 *
 * Scala DOM Types hides all this mess from you using codecs. All those pseudo-boolean
 * attributes would be simply `Attr[Boolean](name, codec)` in your code.
 */
private sealed abstract class Codec[ScalaType, DomType]:

  /**
   * Convert the result of a `dom.Node.getAttribute` call to appropriate Scala type.
   *
   * Note: HTML Attributes are generally optional, and `dom.Node.getAttribute` will return
   * `null` if an attribute is not defined on a given DOM node. However, this decoder is only
   * intended for cases when the attribute is defined.
   */
  def decode(domValue: DomType): ScalaType

  /**
   * Convert desired attribute value to appropriate DOM type. The resulting value should be
   * passed to `dom.Node.setAttribute` call, EXCEPT when resulting value is a `null`. In that
   * case you should call `dom.Node.removeAttribute` instead.
   *
   * We use `null` instead of [[Option]] here to reduce overhead in JS land. This method should
   * not be called by end users anyway, it's the consuming library's job to call this method
   * under the hood.
   */
  def encode(scalaValue: ScalaType): DomType

private object Codec:

  inline def identity[A]: Codec[A, A] = identityInstance.asInstanceOf[Codec[A, A]]
  private val identityInstance: Codec[Any, Any] = new:
    def decode(domValue: Any): Any = domValue
    def encode(scalaValue: Any): Any = scalaValue

  val whitespaceSeparatedStrings: Codec[List[String], String] = new:
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

  val booleanAsAttrPresence: Codec[Boolean, String] = new:
    def decode(domValue: String): Boolean = domValue ne null
    def encode(scalaValue: Boolean): String = if scalaValue then "" else null

  val booleanAsTrueFalseString: Codec[Boolean, String] = new:
    def decode(domValue: String): Boolean = domValue == "true"
    def encode(scalaValue: Boolean): String = if scalaValue then "true" else "false"

  val booleanAsYesNoString: Codec[Boolean, String] = new:
    def decode(domValue: String): Boolean = domValue == "yes"
    def encode(scalaValue: Boolean): String = if scalaValue then "yes" else "no"

  val booleanAsOnOffString: Codec[Boolean, String] = new:
    def decode(domValue: String): Boolean = domValue == "on"
    def encode(scalaValue: Boolean): String = if scalaValue then "on" else "off"

  inline def doubleAsString: Codec[Double, String] = new:
    def decode(domValue: String): Double = domValue.toDouble
    def encode(scalaValue: Double): String = scalaValue.toString

  inline def intAsString: Codec[Int, String] = new:
    def decode(domValue: String): Int = domValue.toInt
    def encode(scalaValue: Int): String = scalaValue.toString
