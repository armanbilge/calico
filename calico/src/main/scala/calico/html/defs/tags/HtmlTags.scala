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

package calico.html.defs.tags

import fs2.dom.*

// #NOTE: GENERATED CODE
//  - This file is generated at compile time from the data in Scala DOM Types
//  - See `project/src/main/scala/calico/html/codegen/DomDefsGenerator.scala` for code generation params
//  - Contribute to https://github.com/raquo/scala-dom-types to add missing tags / attrs / props / etc.

trait HtmlTags[F[_], T[_ <: HtmlElement[F]]] {

  /**
   * Create HTML tag
   *
   * Note: this simply creates an instance of HtmlTag.
   *   - This does not create the element (to do that, call .apply() on the returned tag
   *     instance)
   *   - This does not register this tag name as a custom element
   *     - See https://developer.mozilla.org/en-US/docs/Web/Web_Components/Using_custom_elements
   *
   * @param tagName
   *   \- e.g. "div" or "mwc-input"
   * @tparam Ref
   *   \- type of elements with this tag, e.g. dom.html.Input for "input" tag
   */
  protected def htmlTag[Ref <: HtmlElement[F]](key: String, void: Boolean = false): T[Ref]

  // -- Document Tags --

  /**
   * Represents the root of an HTML or XHTML document. All other elements must be descendants of
   * this element.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/html
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHtmlElement
   */
  lazy val htmlTag: T[HtmlElement[F]] = htmlTag("html")

  /**
   * Represents a collection of metadata about the document, including links to, or definitions
   * of, scripts and style sheets.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/head
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHeadElement
   */
  lazy val headTag: T[HtmlHeadElement[F]] = htmlTag("head")

  /**
   * Defines the base URL for relative URLs in the page.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/base
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLBaseElement
   */
  lazy val baseTag: T[HtmlBaseElement[F]] = htmlTag("base", void = true)

  /**
   * Used to link JavaScript and external CSS with the current HTML document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/link
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLLinkElement
   */
  lazy val linkTag: T[HtmlLinkElement[F]] = htmlTag("link", void = true)

  /**
   * Defines metadata that can't be defined using another HTML element.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/meta
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLMetaElement
   */
  lazy val metaTag: T[HtmlMetaElement[F]] = htmlTag("meta", void = true)

  /**
   * Defines either an internal script or a link to an external script. The script language is
   * JavaScript.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/script
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLScriptElement
   */
  lazy val scriptTag: T[HtmlScriptElement[F]] = htmlTag("script")

  /**
   * Defines alternative content to display when the browser doesn't support scripting.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/noscript
   */
  lazy val noScriptTag: T[HtmlElement[F]] = htmlTag("noscript")

  // -- Embed Tags --

  /**
   * Represents an image.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/img
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLImageElement
   */
  lazy val img: T[HtmlImageElement[F]] = htmlTag("img", void = true)

  /**
   * Represents a nested browsing context, that is an embedded HTML document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/iframe
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLIFrameElement
   */
  lazy val iframe: T[HtmlIFrameElement[F]] = htmlTag("iframe")

  /**
   * Represents a integration point for an external, often non-HTML, application or interactive
   * content.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/embed
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLEmbedElement
   */
  lazy val embedTag: T[HtmlEmbedElement[F]] = htmlTag("embed", void = true)

  /**
   * Represents an external resource, which is treated as an image, an HTML sub-document, or an
   * external resource to be processed by a plug-in.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/object
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLObjectElement
   */
  lazy val objectTag: T[HtmlObjectElement[F]] = htmlTag("object")

  /**
   * Defines parameters for use by plug-ins invoked by object elements.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/param
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLParamElement
   */
  lazy val paramTag: T[HtmlParamElement[F]] = htmlTag("param", void = true)

  /**
   * Represents a video, and its associated audio files and captions, with the necessary
   * interface to play it.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLVideoElement
   */
  lazy val videoTag: T[HtmlVideoElement[F]] = htmlTag("video")

  /**
   * Represents a sound or an audio stream.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/audio
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLAudioElement
   */
  lazy val audioTag: T[HtmlAudioElement[F]] = htmlTag("audio")

  /**
   * Allows the authors to specify alternate media resources for media elements like video or
   * audio
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/source
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLSourceElement
   */
  lazy val sourceTag: T[HtmlSourceElement[F]] = htmlTag("source", void = true)

  /**
   * Allows authors to specify timed text track for media elements like video or audio
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/track
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTrackElement
   */
  lazy val trackTag: T[HtmlTrackElement[F]] = htmlTag("track", void = true)

  /**
   * Represents a bitmap area that scripts can use to render graphics like graphs, games or any
   * visual images on the fly.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/canvas
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement
   */
  lazy val canvasTag: T[HtmlCanvasElement[F]] = htmlTag("canvas")

  /**
   * In conjunction with area, defines an image map.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/map
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLMapElement
   */
  lazy val mapTag: T[HtmlMapElement[F]] = htmlTag("map")

  /**
   * In conjunction with map, defines an image map
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/area
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLAreaElement
   */
  lazy val areaTag: T[HtmlAreaElement[F]] = htmlTag("area", void = true)

  // -- Section Tags --

  /**
   * Represents the content of an HTML document. There is only one body element in a document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/body
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLBodyElement
   */
  lazy val bodyTag: T[HtmlBodyElement[F]] = htmlTag("body")

  /**
   * Defines the header of a page or section. It often contains a logo, the title of the Web
   * site, and a navigational table of content.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/header
   */
  lazy val headerTag: T[HtmlElement[F]] = htmlTag("header")

  /**
   * Defines the footer for a page or section. It often contains a copyright notice, some links
   * to legal information, or addresses to give feedback.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/footer
   */
  lazy val footerTag: T[HtmlElement[F]] = htmlTag("footer")

  /**
   * Heading level 1
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/h1
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHeadingElement
   */
  lazy val h1: T[HtmlHeadingElement[F]] = htmlTag("h1")

  /**
   * Heading level 2
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/h2
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHeadingElement
   */
  lazy val h2: T[HtmlHeadingElement[F]] = htmlTag("h2")

  /**
   * Heading level 3
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/h3
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHeadingElement
   */
  lazy val h3: T[HtmlHeadingElement[F]] = htmlTag("h3")

  /**
   * Heading level 4
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/h4
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHeadingElement
   */
  lazy val h4: T[HtmlHeadingElement[F]] = htmlTag("h4")

  /**
   * Heading level 5
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/h5
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHeadingElement
   */
  lazy val h5: T[HtmlHeadingElement[F]] = htmlTag("h5")

  /**
   * Heading level 6
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/h6
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHeadingElement
   */
  lazy val h6: T[HtmlHeadingElement[F]] = htmlTag("h6")

  // -- Text Tags --

  /**
   * Represents a hyperlink, linking to another resource.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/a
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLAnchorElement
   */
  lazy val a: T[HtmlAnchorElement[F]] = htmlTag("a")

  /**
   * Represents emphasized text.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/em
   */
  lazy val em: T[HtmlElement[F]] = htmlTag("em")

  /**
   * Represents especially important text.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/strong
   */
  lazy val strong: T[HtmlElement[F]] = htmlTag("strong")

  /**
   * Represents a side comment; text like a disclaimer or copyright, which is not essential to
   * the comprehension of the document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/small
   */
  lazy val small: T[HtmlElement[F]] = htmlTag("small")

  /**
   * Strikethrough element, used for that is no longer accurate or relevant.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/s
   */
  lazy val s: T[HtmlElement[F]] = htmlTag("s")

  /**
   * Represents the title of a work being cited.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/cite
   */
  lazy val cite: T[HtmlElement[F]] = htmlTag("cite")

  /**
   * Represents computer code.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/code
   */
  lazy val code: T[HtmlElement[F]] = htmlTag("code")

  /**
   * Subscript tag
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/sub
   */
  lazy val sub: T[HtmlElement[F]] = htmlTag("sub")

  /**
   * Superscript tag.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/sup
   */
  lazy val sup: T[HtmlElement[F]] = htmlTag("sup")

  /**
   * Italicized text.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/i
   */
  lazy val i: T[HtmlElement[F]] = htmlTag("i")

  /**
   * Bold text.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/b
   */
  lazy val b: T[HtmlElement[F]] = htmlTag("b")

  /**
   * Underlined text.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/u
   */
  lazy val u: T[HtmlElement[F]] = htmlTag("u")

  /**
   * Represents text with no specific meaning. This has to be used when no other text-semantic
   * element conveys an adequate meaning, which, in this case, is often brought by global
   * attributes like class, lang, or dir.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/span
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLSpanElement
   */
  lazy val span: T[HtmlSpanElement[F]] = htmlTag("span")

  /**
   * Represents a line break.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/br
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLBRElement
   */
  lazy val br: T[HtmlBrElement[F]] = htmlTag("br", void = true)

  /**
   * Represents a line break opportunity, that is a suggested point for wrapping text in order
   * to improve readability of text split on several lines.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/wbr
   */
  lazy val wbr: T[HtmlElement[F]] = htmlTag("wbr", void = true)

  /**
   * Defines an addition to the document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/ins
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLModElement
   */
  lazy val ins: T[HtmlModElement[F]] = htmlTag("ins")

  /**
   * Defines a remolazy val from the document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/del
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLModElement
   */
  lazy val del: T[HtmlModElement[F]] = htmlTag("del")

  // -- Form Tags --

  /**
   * Represents a form, consisting of controls, that can be submitted to a server for
   * processing.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/form
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLFormElement
   */
  lazy val form: T[HtmlFormElement[F]] = htmlTag("form")

  /**
   * A set of fields.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/fieldset
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLFieldSetElement
   */
  lazy val fieldSet: T[HtmlFieldSetElement[F]] = htmlTag("fieldset")

  /**
   * The caption for a fieldset.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/legend
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLLegendElement
   */
  lazy val legend: T[HtmlLegendElement[F]] = htmlTag("legend")

  /**
   * The caption of a single field
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/label
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLLabelElement
   */
  lazy val label: T[HtmlLabelElement[F]] = htmlTag("label")

  /**
   * A typed data field allowing the user to input data.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement
   */
  lazy val input: T[HtmlInputElement[F]] = htmlTag("input", void = true)

  /**
   * A button
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/button
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLButtonElement
   */
  lazy val button: T[HtmlButtonElement[F]] = htmlTag("button")

  /**
   * A control that allows the user to select one of a set of options.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/select
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLSelectElement
   */
  lazy val select: T[HtmlSelectElement[F]] = htmlTag("select")

  /**
   * A set of predefined options for other controls.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/datalist
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLDataListElement
   */
  lazy val dataList: T[HtmlDataListElement[F]] = htmlTag("datalist")

  /**
   * A set of options, logically grouped.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/optgroup
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLOptGroupElement
   */
  lazy val optGroup: T[HtmlOptGroupElement[F]] = htmlTag("optgroup")

  /**
   * An option in a select element.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/option
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLOptionElement
   */
  lazy val option: T[HtmlOptionElement[F]] = htmlTag("option")

  /**
   * A multiline text edit control.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/textarea
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTextAreaElement
   */
  lazy val textArea: T[HtmlTextAreaElement[F]] = htmlTag("textarea")

  // -- Grouping Tags --

  /**
   * Defines a portion that should be displayed as a paragraph.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/p
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLParagraphElement
   */
  lazy val p: T[HtmlParagraphElement[F]] = htmlTag("p")

  /**
   * Represents a thematic break between paragraphs of a section or article or any longer
   * content.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/hr
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLHRElement
   */
  lazy val hr: T[HtmlHrElement[F]] = htmlTag("hr", void = true)

  /**
   * Indicates that its content is preformatted and that this format must be preserved.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/pre
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLPreElement
   */
  lazy val pre: T[HtmlPreElement[F]] = htmlTag("pre")

  /**
   * Represents a content that is quoted from another source.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/blockquote
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLQuoteElement
   */
  lazy val blockQuote: T[HtmlQuoteElement[F]] = htmlTag("blockquote")

  /**
   * Defines an ordered list of items.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/ol
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLOListElement
   */
  lazy val ol: T[HtmlOListElement[F]] = htmlTag("ol")

  /**
   * Defines an unordered list of items.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/ul
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLUListElement
   */
  lazy val ul: T[HtmlUListElement[F]] = htmlTag("ul")

  /**
   * Defines an item of an list.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/li
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLLIElement
   */
  lazy val li: T[HtmlLiElement[F]] = htmlTag("li")

  /**
   * Defines a definition list; a list of terms and their associated definitions.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dl
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLDListElement
   */
  lazy val dl: T[HtmlDListElement[F]] = htmlTag("dl")

  /**
   * Represents a term defined by the next dd
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dt
   */
  lazy val dt: T[HtmlElement[F]] = htmlTag("dt")

  /**
   * Represents the definition of the terms immediately listed before it.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dd
   */
  lazy val dd: T[HtmlElement[F]] = htmlTag("dd")

  /**
   * Represents a figure illustrated as part of the document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/figure
   */
  lazy val figure: T[HtmlElement[F]] = htmlTag("figure")

  /**
   * Represents the legend of a figure.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/figcaption
   */
  lazy val figCaption: T[HtmlElement[F]] = htmlTag("figcaption")

  /**
   * Represents a generic container with no special meaning.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/div
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLDivElement
   */
  lazy val div: T[HtmlDivElement[F]] = htmlTag("div")

  // -- Table Tags --

  /**
   * Represents data with more than one dimension.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/table
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableElement
   */
  lazy val table: T[HtmlTableElement[F]] = htmlTag("table")

  /**
   * The title of a table.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/caption
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableCaptionElement
   */
  lazy val caption: T[HtmlTableCaptionElement[F]] = htmlTag("caption")

  /**
   * A set of columns.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/colgroup
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableColElement
   */
  lazy val colGroup: T[HtmlTableColElement[F]] = htmlTag("colgroup")

  /**
   * A single column.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/col
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableColElement
   */
  lazy val col: T[HtmlTableColElement[F]] = htmlTag("col", void = true)

  /**
   * The table body.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/tbody
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableSectionElement
   */
  lazy val tbody: T[HtmlTableSectionElement[F]] = htmlTag("tbody")

  /**
   * The table headers.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/thead
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableSectionElement
   */
  lazy val thead: T[HtmlTableSectionElement[F]] = htmlTag("thead")

  /**
   * The table footer.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/tfoot
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableSectionElement
   */
  lazy val tfoot: T[HtmlTableSectionElement[F]] = htmlTag("tfoot")

  /**
   * A single row in a table.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/tr
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableRowElement
   */
  lazy val tr: T[HtmlTableRowElement[F]] = htmlTag("tr")

  /**
   * A single cell in a table.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/td
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableCellElement
   */
  lazy val td: T[HtmlTableCellElement[F]] = htmlTag("td")

  /**
   * A header cell in a table.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/th
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTableCellElement
   */
  lazy val th: T[HtmlTableCellElement[F]] = htmlTag("th")

  // -- Misc Tags --

  /**
   * Defines the title of the document, shown in a browser's title bar or on the page's tab. It
   * can only contain text and any contained tags are not interpreted.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/title
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLTitleElement
   */
  lazy val titleTag: T[HtmlTitleElement[F]] = htmlTag("title")

  /**
   * Used to write inline CSS.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/style
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLStyleElement
   */
  lazy val styleTag: T[HtmlStyleElement[F]] = htmlTag("style")

  /**
   * Represents a generic section of a document, i.e., a thematic grouping of content, typically
   * with a heading.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/section
   */
  lazy val sectionTag: T[HtmlElement[F]] = htmlTag("section")

  /**
   * Represents a section of a page that links to other pages or to parts within the page: a
   * section with navigation links.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/nav
   */
  lazy val navTag: T[HtmlElement[F]] = htmlTag("nav")

  /**
   * Defines self-contained content that could exist independently of the rest of the content.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/article
   */
  lazy val articleTag: T[HtmlElement[F]] = htmlTag("article")

  /**
   * Defines some content loosely related to the page content. If it is removed, the remaining
   * content still makes sense.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/aside
   */
  lazy val asideTag: T[HtmlElement[F]] = htmlTag("aside")

  /**
   * Defines a section containing contact information.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/address
   */
  lazy val addressTag: T[HtmlElement[F]] = htmlTag("address")

  /**
   * Defines the main or important content in the document. There is only one main element in
   * the document.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/main
   */
  lazy val mainTag: T[HtmlElement[F]] = htmlTag("main")

  /**
   * An inline quotation.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/q
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLQuoteElement
   */
  lazy val q: T[HtmlQuoteElement[F]] = htmlTag("q")

  /**
   * Represents a term whose definition is contained in its nearest ancestor content.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dfn
   */
  lazy val dfn: T[HtmlElement[F]] = htmlTag("dfn")

  /**
   * An abbreviation or acronym; the expansion of the abbreviation can be represented in the
   * title attribute.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/abbr
   */
  lazy val abbr: T[HtmlElement[F]] = htmlTag("abbr")

  /**
   * Associates to its content a machine-readable equivalent.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/data
   */
  lazy val dataTag: T[HtmlElement[F]] = htmlTag("data")

  /**
   * Represents a date and time value; the machine-readable equivalent can be represented in the
   * datetime attribute
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/time
   */
  lazy val timeTag: T[HtmlElement[F]] = htmlTag("time")

  /**
   * Represents a variable.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/var
   */
  lazy val varTag: T[HtmlElement[F]] = htmlTag("var")

  /**
   * Represents the output of a program or a computer.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/samp
   */
  lazy val samp: T[HtmlElement[F]] = htmlTag("samp")

  /**
   * Represents user input, often from a keyboard, but not necessarily.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/kbd
   */
  lazy val kbd: T[HtmlElement[F]] = htmlTag("kbd")

  /**
   * Defines a mathematical formula.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/math
   */
  lazy val mathTag: T[HtmlElement[F]] = htmlTag("math")

  /**
   * Represents text highlighted for reference purposes, that is for its relevance in another
   * context.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/mark
   */
  lazy val mark: T[HtmlElement[F]] = htmlTag("mark")

  /**
   * Represents content to be marked with ruby annotations, short runs of text presented
   * alongside the text. This is often used in conjunction with East Asian language where the
   * annotations act as a guide for pronunciation, like the Japanese furigana .
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/ruby
   */
  lazy val ruby: T[HtmlElement[F]] = htmlTag("ruby")

  /**
   * Represents the text of a ruby annotation.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/rt
   */
  lazy val rt: T[HtmlElement[F]] = htmlTag("rt")

  /**
   * Represents parenthesis around a ruby annotation, used to display the annotation in an
   * alternate way by browsers not supporting the standard display for annotations.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/rp
   */
  lazy val rp: T[HtmlElement[F]] = htmlTag("rp")

  /**
   * Represents text that must be isolated from its surrounding for bidirectional text
   * formatting. It allows embedding a span of text with a different, or unknown,
   * directionality.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/bdi
   */
  lazy val bdi: T[HtmlElement[F]] = htmlTag("bdi")

  /**
   * Represents the directionality of its children, in order to explicitly override the Unicode
   * bidirectional algorithm.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/bdo
   */
  lazy val bdo: T[HtmlElement[F]] = htmlTag("bdo")

  /**
   * A key-pair generator control.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/keygen
   */
  lazy val keyGenTag: T[HtmlElement[F]] = htmlTag("keygen")

  /**
   * The result of a calculation
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/output
   */
  lazy val outputTag: T[HtmlElement[F]] = htmlTag("output")

  /**
   * A progress completion bar
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/progress
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLProgressElement
   */
  lazy val progressTag: T[HtmlProgressElement[F]] = htmlTag("progress")

  /**
   * A scalar measurement within a known range.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/meter
   */
  lazy val meterTag: T[HtmlElement[F]] = htmlTag("meter")

  /**
   * A widget from which the user can obtain additional information or controls.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/details
   */
  lazy val detailsTag: T[HtmlElement[F]] = htmlTag("details")

  /**
   * A summary, caption, or legend for a given details.
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/summary
   */
  lazy val summaryTag: T[HtmlElement[F]] = htmlTag("summary")

  /**
   * A command that the user can invoke.
   *
   * @see
   *   https://www.w3.org/TR/2011/WD-html5-author-20110809/the-command-element.html
   */
  lazy val commandTag: T[HtmlElement[F]] = htmlTag("command")

  /**
   * A list of commands
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTML/Element/menu
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/API/HTMLMenuElement
   */
  lazy val menuTag: T[HtmlMenuElement[F]] = htmlTag("menu")

}
