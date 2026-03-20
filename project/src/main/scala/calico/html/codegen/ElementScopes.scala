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

package calico.html.codegen

private[codegen] object ElementScopes {

  val specificElementTypes: Set[String] = Set(
    "HtmlAnchorElement",
    "HtmlButtonElement",
    "HtmlInputElement",
    "HtmlOptionElement",
    "HtmlSelectElement",
    "HtmlTextAreaElement"
  )

  val attrScopes: Map[String, Set[String]] = Map(
    "href" -> Set("HtmlAnchorElement"),
    "formId" -> Set(
      "HtmlInputElement",
      "HtmlButtonElement",
      "HtmlSelectElement",
      "HtmlTextAreaElement"),
    "maxAttr" -> Set("HtmlInputElement"),
    "minAttr" -> Set("HtmlInputElement"),
    "stepAttr" -> Set("HtmlInputElement")
  )

  val propScopes: Map[String, Set[String]] = Map(
    "checked" -> Set("HtmlInputElement"),
    "indeterminate" -> Set("HtmlInputElement"),
    "defaultChecked" -> Set("HtmlInputElement"),
    "defaultValue" -> Set("HtmlInputElement", "HtmlTextAreaElement"),
    "value" -> Set(
      "HtmlInputElement",
      "HtmlTextAreaElement",
      "HtmlSelectElement",
      "HtmlOptionElement"),
    "selected" -> Set("HtmlOptionElement"),
    "multiple" -> Set("HtmlInputElement", "HtmlSelectElement"),
    "disabled" -> Set(
      "HtmlInputElement",
      "HtmlButtonElement",
      "HtmlSelectElement",
      "HtmlTextAreaElement",
      "HtmlOptionElement"),
    "readOnly" -> Set("HtmlInputElement", "HtmlTextAreaElement")
  )

  def elementBoundsForAttr(scalaName: String): List[String] =
    attrScopes.get(scalaName) match {
      case Some(elements) => elements.toList.sorted
      case None => List("HtmlElement")
    }

  def elementBoundsForProp(scalaName: String): List[String] =
    propScopes.get(scalaName) match {
      case Some(elements) => elements.toList.sorted
      case None => List("HtmlElement")
    }
}
