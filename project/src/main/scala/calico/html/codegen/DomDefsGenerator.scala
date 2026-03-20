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

    val htmlTags = {
      val traitName = "HtmlTags"
      val traitNameWithParams = s"$traitName[F[_]](using Async[F])"

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

    val htmlAttrDefsList = defGroups.htmlAttrDefGroups.flatMap(_._2)

    val htmlAttrs = {
      val traitName = "HtmlAttrs"

      val fileContent = generator.generatePhantomHtmlAttrsTrait(
        defGroups = defGroups.htmlAttrDefGroups,
        traitName = s"$traitName[F[_]]"
      )

      writeToFile(generator.attrDefsPackagePath, traitName, fileContent)
    }

    def transformAriaAttrDomName(ariaAttrName: String): String = {
      if (ariaAttrName.startsWith("aria-")) {
        ariaAttrName.substring(5)
      } else {
        throw new Exception(s"Aria attribute does not start with `aria-`: $ariaAttrName")
      }
    }

    val ariaAttrDefsList = defGroups
      .ariaAttrDefGroups
      .flatMap(_._2)
      .map(d => d.copy(domName = transformAriaAttrDomName(d.domName)))

    val ariaAttrs = {
      val traitName = "AriaAttrs"

      val fileContent = generator.generatePhantomAriaAttrsTrait(
        defGroups = defGroups.ariaAttrDefGroups.map {
          case (key, vals) =>
            (key, vals.map(d => d.copy(domName = transformAriaAttrDomName(d.domName))))
        },
        traitName = s"$traitName[F[_]]"
      )

      writeToFile(generator.attrDefsPackagePath, traitName, fileContent)
    }

    val propDefsList = defGroups.propDefGroups.flatMap(_._2)

    val htmlProps = {
      val traitName = "Props"

      val fileContent = generator.generatePhantomPropsTrait(
        defGroups = defGroups.propDefGroups,
        traitName = s"$traitName[F[_]]"
      )

      writeToFile(generator.propDefsPackagePath, traitName, fileContent)
    }

    val validInstances = {
      val traitName = "GeneratedValidInstances"

      val fileContent = generator.generateValidInstances(
        htmlAttrDefs = htmlAttrDefsList,
        ariaAttrDefs = ariaAttrDefsList,
        propDefs = propDefsList
      )

      writeToFile(generator.keysPackagePath, traitName, fileContent)
    }

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
                vals.map(attr =>
                  attr.copy(scalaJsEventType = s"F, fs2.${attr.scalaJsEventType}[F]")))
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
                      attr.copy(scalaJsEventType = s"F, fs2.${attr.scalaJsEventType}[F]")))
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

    List(
      List(htmlTags, htmlAttrs, ariaAttrs, htmlProps, validInstances).sequence,
      eventProps).parFlatSequence
  }
}
