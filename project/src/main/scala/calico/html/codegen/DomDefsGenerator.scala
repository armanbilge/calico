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

import com.raquo.domtypes.codegen.DefType.LazyVal
import com.raquo.domtypes.codegen.{
  CanonicalDefGroups,
  CanonicalGenerator,
  CodeFormatting,
  SourceRepr
}
import cats.effect.IO
import cats.syntax.all._
import com.raquo.domtypes.codegen.DefType
import com.raquo.domtypes.codegen.generators.AttrsTraitGenerator
import com.raquo.domtypes.codegen.generators.EventPropsTraitGenerator
import com.raquo.domtypes.codegen.generators.PropsTraitGenerator
import com.raquo.domtypes.codegen.generators.TagsTraitGenerator
import com.raquo.domtypes.common
import com.raquo.domtypes.common.TagType
import com.raquo.domtypes.common.{HtmlTagType, SvgTagType}
import com.raquo.domtypes.defs.styles.StyleTraitDefs
import java.io.File

object DomDefsGenerator {

  def generate(srcManaged: File): IO[List[File]] = {
    val defGroups = new CanonicalDefGroups()
    val generator = new CalicoGenerator(srcManaged)

    def writeToFile(packagePath: String, fileName: String, fileContent: String): IO[File] =
      IO {
        generator.writeToFile(
          packagePath = packagePath,
          fileName = fileName,
          fileContent = fileContent
        )
      }

    // -- HTML tags --

    val htmlTags = {
      val traitName = "HtmlTags"
      val traitNameWithParams = s"$traitName[F[_]]"

      val fileContent = generator.generateTagsTrait(
        tagType = HtmlTagType,
        defGroups = defGroups.htmlTagsDefGroups,
        printDefGroupComments = true,
        traitCommentLines = Nil,
        traitModifiers = List("private"),
        traitName = traitNameWithParams,
        keyKind = "HtmlTag[F, _]",
        baseImplDefComments = List(
          "Create HTML tag",
          "",
          "Note: this simply creates an instance of HtmlTag.",
          " - This does not create the element (to do that, call .apply() on the returned tag instance)",
          " - This does not register this tag name as a custom element",
          "   - See https://developer.mozilla.org/en-US/docs/Web/Web_Components/Using_custom_elements",
          "",
          "@param tagName - e.g. \"div\" or \"mwc-input\"",
          "@tparam Ref - type of elements with this tag, e.g. dom.html.Input for \"input\" tag"
        ),
        keyImplName = "htmlTag",
        defType = LazyVal
      )

      writeToFile(generator.tagDefsPackagePath, traitName, fileContent)
    }

    // -- SVG tags --

    // {
    // val traitName = "SvgTags"

    // val fileContent = generator.generateTagsTrait(
    // tagType = SvgTagType,
    // defGroups = defGroups.svgTagsDefGroups,
    // printDefGroupComments = false,
    // traitCommentLines = Nil,
    // traitName = traitName,
    // keyKind = "SvgTag",
    // baseImplDefComments = List(
    // "Create SVG tag",
    // "",
    // "Note: this simply creates an instance of HtmlTag.",
    // " - This does not create the element (to do that, call .apply() on the returned tag instance)",
    // "",
    // "@param tagName - e.g. \"circle\"",
    // "",
    // "@tparam Ref    - type of elements with this tag, e.g. dom.svg.Circle for \"circle\" tag"
    // ),
    // keyImplName = "svgTag",
    // defType = LazyVal
    // )

    // generator.writeToFile(
    // packagePath = generator.tagDefsPackagePath,
    // fileName = traitName,
    // fileContent = fileContent
    // )
    // }

    // -- HTML attributes --

    val htmlAttrs = {
      val traitName = "HtmlAttrs"
      val traitNameWithParams = s"$traitName[F[_]]"

      val fileContent = generator.generateAttrsTrait(
        defGroups = defGroups.htmlAttrDefGroups.map {
          case (key, vals) =>
            (key, vals.map(attr => attr.copy(scalaValueType = "F, " + attr.scalaValueType)))
        },
        printDefGroupComments = false,
        traitCommentLines = Nil,
        traitModifiers = List("private"),
        traitName = traitNameWithParams,
        keyKind = "HtmlAttr",
        implNameSuffix = "HtmlAttr",
        baseImplDefComments = List(
          "Create HTML attribute (Note: for SVG attrs, use L.svg.svgAttr)",
          "",
          "@param key   - name of the attribute, e.g. \"value\"",
          "@param codec - used to encode V into String, e.g. StringAsIsCodec",
          "",
          "@tparam V    - value type for this attr in Scala"
        ),
        baseImplName = "htmlAttr",
        namespaceImports = Nil,
        namespaceImpl = _ => ???,
        transformAttrDomName = identity,
        defType = LazyVal
      )

      writeToFile(generator.attrDefsPackagePath, traitName, fileContent)
    }

    // -- SVG attributes --

    // {
    // val traitName = "SvgAttrs"

    // val fileContent = generator.generateAttrsTrait(
    // defGroups = defGroups.svgAttrDefGroups,
    // printDefGroupComments = false,
    // traitName = traitName,
    // traitCommentLines = Nil,
    // keyKind = "SvgAttr",
    // baseImplDefComments = List(
    // "Create SVG attribute (Note: for HTML attrs, use L.htmlAttr)",
    // "",
    // "@param key   - name of the attribute, e.g. \"value\"",
    // "@param codec - used to encode V into String, e.g. StringAsIsCodec",
    // "",
    // "@tparam V    - value type for this attr in Scala"
    // ),
    // implNameSuffix = "SvgAttr",
    // baseImplName = "svgAttr",
    // namespaceImports = Nil,
    // namespaceImpl = SourceRepr(_),
    // transformAttrDomName = identity,
    // defType = LazyVal
    // )

    // generator.writeToFile(
    // packagePath = generator.attrDefsPackagePath,
    // fileName = traitName,
    // fileContent = fileContent
    // )
    // }

    // -- ARIA attributes --

    val ariaAttrs = {
      val traitName = "AriaAttrs"
      val traitNameWithParams = s"$traitName[F[_]]"

      def transformAttrDomName(ariaAttrName: String): String = {
        if (ariaAttrName.startsWith("aria-")) {
          ariaAttrName.substring(5)
        } else {
          throw new Exception(s"Aria attribute does not start with `aria-`: $ariaAttrName")
        }
      }

      val fileContent = generator.generateAttrsTrait(
        defGroups = defGroups.ariaAttrDefGroups.map {
          case (key, vals) =>
            (key, vals.map(attr => attr.copy(scalaValueType = "F, " + attr.scalaValueType)))
        },
        printDefGroupComments = false,
        traitModifiers = List("private"),
        traitName = traitNameWithParams,
        traitCommentLines = Nil,
        keyKind = "AriaAttr",
        implNameSuffix = "AriaAttr",
        baseImplDefComments = List(
          "Create ARIA attribute (Note: for HTML attrs, use L.htmlAttr)",
          "",
          "@param key   - suffix of the attribute, without \"aria-\" prefix, e.g. \"labelledby\"",
          "@param codec - used to encode V into String, e.g. StringAsIsCodec",
          "",
          "@tparam V    - value type for this attr in Scala"
        ),
        baseImplName = "ariaAttr",
        namespaceImports = Nil,
        namespaceImpl = _ => ???,
        transformAttrDomName = transformAttrDomName,
        defType = LazyVal
      )

      writeToFile(generator.attrDefsPackagePath, traitName, fileContent)
    }

    // -- HTML props --

    val htmlProps = {
      val traitName = "Props"
      val traitNameWithParams = s"$traitName[F[_]]"

      val fileContent = generator.generatePropsTrait(
        defGroups = defGroups.propDefGroups.map {
          case (key, vals) =>
            (key, vals.map(attr => attr.copy(scalaValueType = "F, " + attr.scalaValueType)))
        },
        printDefGroupComments = true,
        traitCommentLines = Nil,
        traitModifiers = List("private"),
        traitName = traitNameWithParams,
        keyKind = "Prop",
        implNameSuffix = "Prop",
        baseImplDefComments = List(
          "Create custom HTML element property",
          "",
          "@param key   - name of the prop in JS, e.g. \"value\"",
          "@param codec - used to encode V into DomV, e.g. StringAsIsCodec,",
          "",
          "@tparam V    - value type for this prop in Scala",
          "@tparam DomV - value type for this prop in the underlying JS DOM."
        ),
        baseImplName = "prop",
        defType = LazyVal
      )

      writeToFile(generator.propDefsPackagePath, traitName, fileContent)
    }

    // -- Event props --

    val eventProps = {
      val baseTraitName = "GlobalEventProps"
      val baseTraitNameWithParams = s"$baseTraitName[F[_]]"

      val subTraits = List(
        ("WindowEventProps", "WindowEventProps[F[_]]", defGroups.windowEventPropDefGroups),
        ("DocumentEventProps", "DocumentEventProps[F[_]]", defGroups.documentEventPropDefGroups)
      )

      val global = {
        val fileContent = generator.generateEventPropsTrait(
          defSources = defGroups.globalEventPropDefGroups.map {
            case (key, vals) =>
              (
                key,
                vals.map(attr => attr.copy(scalaJsEventType = "F, " + attr.scalaJsEventType)))
          },
          printDefGroupComments = true,
          traitCommentLines = Nil,
          traitModifiers = List("private"),
          traitName = baseTraitNameWithParams,
          traitExtends = Nil,
          traitThisType = None,
          baseImplDefComments = List(
            "Create custom event property",
            "",
            "@param key - event type in JS, e.g. \"click\"",
            "",
            "@tparam Ev - event type in JS, e.g. dom.MouseEvent"
          ),
          outputBaseImpl = true,
          keyKind = "EventProp",
          keyImplName = "eventProp",
          defType = LazyVal
        )

        writeToFile(generator.eventPropDefsPackagePath, baseTraitName, fileContent)
      }

      List(
        subTraits.traverse {
          case (traitName, traitNameWithParams, eventPropsDefGroups) =>
            val fileContent = generator.generateEventPropsTrait(
              defSources = eventPropsDefGroups.map {
                case (key, vals) =>
                  (
                    key,
                    vals.map(attr =>
                      attr.copy(scalaJsEventType = "F, " + attr.scalaJsEventType)))
              },
              printDefGroupComments = true,
              traitCommentLines = List(eventPropsDefGroups.head._1),
              traitModifiers = List("private"),
              traitName = traitNameWithParams,
              traitExtends = Nil,
              traitThisType = Some(baseTraitName + "[F]"),
              baseImplDefComments = Nil,
              outputBaseImpl = false,
              keyKind = "EventProp",
              keyImplName = "eventProp",
              defType = LazyVal
            )
            writeToFile(generator.eventPropDefsPackagePath, traitName, fileContent)
        },
        global.map(_.pure[List])
      ).parFlatSequence
    }

    // -- Style props --

    // {
    // val traitName = "StyleProps"

    // val fileContent = generator.generateStylePropsTrait(
    // defSources = defGroups.stylePropDefGroups,
    // printDefGroupComments = true,
    // traitCommentLines = Nil,
    // traitName = traitName,
    // keyKind = "StyleProp",
    // keyKindAlias = "StyleProp",
    // setterType = "StyleSetter",
    // setterTypeAlias = "SS",
    // derivedKeyKind = "DerivedStyleProp",
    // derivedKeyKindAlias = "DSP",
    // baseImplDefComments = List(
    // "Create custom CSS property",
    // "",
    // "@param key - name of CSS property, e.g. \"font-weight\"",
    // "",
    // "@tparam V  - type of values recognized by JS for this property, e.g. Int",
    // "             Note: String is always allowed regardless of the type you put here.",
    // "             If unsure, use String type as V."
    // ),
    // baseImplName = "styleProp",
    // defType = LazyVal,
    // lengthUnitsNumType = "Int",
    // outputUnitTraits = true
    // )

    // generator.writeToFile(
    // packagePath = generator.stylePropDefsPackagePath,
    // fileName = traitName,
    // fileContent = fileContent
    // )
    // }

    // -- Style keyword traits

    // {
    // StyleTraitDefs.defs.foreach { styleTrait =>
    // val fileContent = generator.generateStyleKeywordsTrait(
    // defSources = styleTrait.keywordDefGroups,
    // printDefGroupComments = styleTrait.keywordDefGroups.length > 1,
    // traitCommentLines = Nil,
    // traitName = styleTrait.scalaName.replace("[_]", ""),
    // extendsTraits = styleTrait.extendsTraits.map(_.replace("[_]", "")),
    // extendsUnitTraits = styleTrait.extendsUnits,
    // propKind = "StyleProp",
    // keywordType = "StyleSetter",
    // derivedKeyKind = "DerivedStyleProp",
    // lengthUnitsNumType = "Int",
    // defType = LazyVal,
    // outputUnitTypes = true,
    // allowSuperCallInOverride = false // can't access lazy val from `super`
    // )

    // generator.writeToFile(
    // packagePath = generator.styleTraitsPackagePath(),
    // fileName = styleTrait.scalaName.replace("[_]", ""),
    // fileContent = fileContent
    // )
    // }
    // }
    List(List(htmlTags, htmlAttrs, ariaAttrs, htmlProps).sequence, eventProps).parFlatSequence
  }
}
