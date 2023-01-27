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

package calico
package html

private object encoders:
  inline def identity[A]: A => A = _identity.asInstanceOf[A => A]
  private val _identity: Any => Any = x => x

  val whitespaceSeparatedStrings: List[String] => String = strings =>
    if strings.isEmpty then ""
    else
      var acc = strings.head
      var tail = strings.tail
      while tail.nonEmpty do
        acc += " " + tail.head
        tail = tail.tail
      acc

  val booleanAsAttrPresence: Boolean => String =
    if _ then "" else null

  val booleanAsTrueFalseString: Boolean => String =
    _.toString

  val booleanAsYesNoString: Boolean => String =
    if _ then "yes" else "no"

  val booleanAsOnOffString: Boolean => String =
    if _ then "on" else "off"

  val doubleAsString: Double => String = _.toString

  val intAsString: Int => String = _.toString
