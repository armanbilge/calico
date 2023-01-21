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

private final class Codec[ScalaType, DomType](
    val decode: DomType => ScalaType,
    val encode: ScalaType => DomType
)

object Codec:

  inline def identity[A]: Codec[A, A] = identityInstance.asInstanceOf[Codec[A, A]]
  private val identityInstance: Codec[Any, Any] = Codec(Predef.identity, Predef.identity)

  val whitespaceSeparatedStrings: Codec[List[String], String] = Codec(
    _.split(" ").toList,
    scalaValue =>
      if scalaValue.isEmpty then ""
      else
        var acc = scalaValue.head
        var tail = scalaValue.tail
        while tail.nonEmpty do
          acc += " " + tail.head
          tail = tail.tail
        acc
  )

  val booleanAsAttrPresence: Codec[Boolean, String] = Codec(
    _ ne null,
    if _ then "" else null
  )

  val booleanAsTrueFalseString: Codec[Boolean, String] = Codec(
    _ == "true",
    if _ then "true" else "false"
  )

  val booleanAsYesNoString: Codec[Boolean, String] = Codec(
    _ == "yes",
    if _ then "yes" else "no"
  )

  val booleanAsOnOffString: Codec[Boolean, String] = Codec(
    _ == "on",
    if _ then "on" else "off"
  )

  val doubleAsString: Codec[Double, String] = Codec(
    _.toDouble, // @TODO this can throw exception. How do we handle this?
    _.toString
  )

  val intAsString: Codec[Int, String] = Codec(
    _.toInt, // @TODO this can throw exception. How do we handle this?
    _.toString
  )
