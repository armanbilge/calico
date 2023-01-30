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

import com.raquo.domtypes.codegen.CanonicalGenerator
import com.raquo.domtypes.codegen.CodeFormatting
import com.raquo.domtypes.codegen.DefType
import com.raquo.domtypes.codegen.generators.AttrsTraitGenerator
import com.raquo.domtypes.codegen.generators.EventPropsTraitGenerator
import com.raquo.domtypes.codegen.generators.PropsTraitGenerator
import com.raquo.domtypes.codegen.generators.TagsTraitGenerator
import com.raquo.domtypes.common.AttrDef
import com.raquo.domtypes.common.EventPropDef
import com.raquo.domtypes.common.HtmlTagType
import com.raquo.domtypes.common.PropDef
import com.raquo.domtypes.common.SvgTagType
import com.raquo.domtypes.common.TagDef
import com.raquo.domtypes.common.TagType
import java.io.File
import java.nio.file.Paths

private[codegen] class CalicoGenerator(srcManaged: File)
    extends CanonicalGenerator(
      baseOutputDirectoryPath = srcManaged.getPath,
      basePackagePath = "calico.html",
      standardTraitCommentLines = List(
        "#NOTE: GENERATED CODE",
        " - This file is generated at compile time from the data in Scala DOM Types",
        " - See `project/src/main/scala/calico/html/codegen/DomDefsGenerator.scala` for code generation params",
        " - Contribute to https://github.com/raquo/scala-dom-types to add missing tags / attrs / props / etc."
      ),
      format = CodeFormatting()
    ) {

  override val baseScalaJsHtmlElementType: String = "HtmlElement[F]"

  override def defsPackagePath: String = basePackagePath

  override def tagDefsPackagePath: String = defsPackagePath

  override def attrDefsPackagePath: String = defsPackagePath

  override def propDefsPackagePath: String = defsPackagePath

  override def eventPropDefsPackagePath: String = defsPackagePath

  override def stylePropDefsPackagePath: String = defsPackagePath

  override def keysPackagePath: String = basePackagePath

  override def tagKeysPackagePath: String = basePackagePath

  override val codecsImport: String = ""

  private def transformCodecName(codec: String) = codec match {
    case c if c.endsWith("AsIs") => s"encoders.identity[${c.dropRight(4)}]"
    case c => s"encoders.${c(0).toLower}${c.substring(1)}"
  }

  override def generateTagsTrait(
      tagType: TagType,
      defGroups: List[(String, List[TagDef])],
      printDefGroupComments: Boolean,
      traitCommentLines: List[String],
      traitModifiers: List[String],
      traitName: String,
      keyKind: String,
      baseImplDefComments: List[String],
      keyImplName: String,
      defType: DefType): String = {
    val (defs, defGroupComments) = defsAndGroupComments(defGroups, printDefGroupComments)

    val baseImplDef = if (tagType == HtmlTagType) {
      List(
        s"@inline private[calico] def ${keyImplName}[$scalaJsElementTypeParam <: $baseScalaJsHtmlElementType](key: String, void: Boolean = false): HtmlTag[F, $scalaJsElementTypeParam] = HtmlTag[F, $scalaJsElementTypeParam](key, void)"
      )
    } else {
      List(
        s"@inline private[calico] def ${keyImplName}[$scalaJsElementTypeParam <: $baseScalaJsSvgElementType](key: String): ${keyKind}[$scalaJsElementTypeParam] = ${keyKindConstructor(keyKind)}(key)"
      )
    }

    val headerLines = List(
      s"package $tagDefsPackagePath",
      "",
      "import cats.effect.kernel.Async",
      "import fs2.dom.*",
      ""
    ) ++ standardTraitCommentLines.map("// " + _)

    new TagsTraitGenerator(
      defs = defs,
      defGroupComments = defGroupComments,
      headerLines = headerLines,
      traitCommentLines = traitCommentLines,
      traitModifiers = traitModifiers,
      traitName = traitName,
      traitExtends = Nil,
      traitThisType = None,
      defType = _ => defType,
      keyType = tag => "HtmlTag[F, " + TagDefMapper.extractFs2DomElementType(tag) + "]",
      keyImplName = _ => keyImplName,
      baseImplDefComments = baseImplDefComments,
      baseImplDef = baseImplDef,
      outputImplDefs = true,
      format = format
    ).printTrait().getOutput()
  }

  override def generateAttrsTrait(
      defGroups: List[(String, List[AttrDef])],
      printDefGroupComments: Boolean,
      traitCommentLines: List[String],
      traitModifiers: List[String],
      traitName: String,
      keyKind: String,
      implNameSuffix: String,
      baseImplDefComments: List[String],
      baseImplName: String,
      namespaceImports: List[String],
      namespaceImpl: String => String,
      transformAttrDomName: String => String,
      defType: DefType): String = {
    val (defs, defGroupComments) = defsAndGroupComments(defGroups, printDefGroupComments)

    val tagTypes = defs.foldLeft(List[TagType]())((acc, k) => (acc :+ k.tagType).distinct)
    if (tagTypes.size > 1) {
      throw new Exception(
        "Sorry, generateAttrsTrait does not support mixing attrs of different types in one call. You can contribute a PR (please contact us first), or bypass this limitation by calling AttrsTraitGenerator manually.")
    }
    val tagType = tagTypes.head

    val baseImplDef = if (tagType == SvgTagType) {
      List(
        s"@inline private[calico] def ${baseImplName}[V](key: String, encode: V => String, namespace: Option[String]): ${keyKind}[V] = ${keyKindConstructor(keyKind)}(key, encode, namespace)"
      )
    } else {
      List(
        s"@inline private[calico] def ${baseImplName}[V](key: String, encode: V => String): ${keyKind}[F, V] = ${keyKindConstructor(keyKind)}(key, encode)"
      )
    }

    val headerLines = List(
      s"package $attrDefsPackagePath",
      "",
      keyTypeImport(keyKind),
      codecsImport
    ) ++ namespaceImports ++ List("") ++ standardTraitCommentLines.map("// " + _)

    new AttrsTraitGenerator(
      defs = defs.map(d => d.copy(domName = transformAttrDomName(d.domName))),
      defGroupComments = defGroupComments,
      headerLines = headerLines,
      traitCommentLines = traitCommentLines,
      traitModifiers = traitModifiers,
      traitName = traitName,
      traitExtends = Nil,
      traitThisType = None,
      defType = _ => defType,
      keyKind = keyKind,
      keyImplName = attr => attrImplName(attr.codec, implNameSuffix),
      baseImplDefComments = baseImplDefComments,
      baseImplName = baseImplName,
      baseImplDef = baseImplDef,
      transformCodecName = transformCodecName,
      namespaceImpl = namespaceImpl,
      outputImplDefs = true,
      format = format
    ).printTrait().getOutput()
  }

  override def generatePropsTrait(
      defGroups: List[(String, List[PropDef])],
      printDefGroupComments: Boolean,
      traitCommentLines: List[String],
      traitModifiers: List[String],
      traitName: String,
      keyKind: String,
      implNameSuffix: String,
      baseImplDefComments: List[String],
      baseImplName: String,
      defType: DefType): String = {

    val (defs, defGroupComments) = defsAndGroupComments(defGroups, printDefGroupComments)

    val baseImplDef = List(
      s"@inline private[calico] def ${baseImplName}[V, DomV](key: String, encode: V => DomV): ${keyKind}[F, V, DomV] = ${keyKindConstructor(keyKind)}(key, encode)"
    )

    val headerLines = List(
      s"package $propDefsPackagePath",
      "",
      keyTypeImport(keyKind),
      codecsImport,
      ""
    ) ++ standardTraitCommentLines.map("// " + _)

    new PropsTraitGenerator(
      defs = defs,
      defGroupComments = defGroupComments,
      headerLines = headerLines,
      traitCommentLines = traitCommentLines,
      traitModifiers = traitModifiers,
      traitName = traitName,
      traitExtends = Nil,
      traitThisType = None,
      defType = _ => defType,
      keyKind = keyKind,
      keyImplName = prop => propImplName(prop.codec, implNameSuffix),
      baseImplDefComments = baseImplDefComments,
      baseImplName = baseImplName,
      baseImplDef = baseImplDef,
      transformCodecName = transformCodecName,
      outputImplDefs = true,
      format = format
    ).printTrait().getOutput()
  }

  override def generateEventPropsTrait(
      defSources: List[(String, List[EventPropDef])],
      printDefGroupComments: Boolean,
      traitCommentLines: List[String],
      traitModifiers: List[String],
      traitName: String,
      traitExtends: List[String],
      traitThisType: Option[String],
      baseImplDefComments: List[String],
      outputBaseImpl: Boolean,
      keyKind: String,
      keyImplName: String,
      defType: DefType): String = {
    val (defs, defGroupComments) = defsAndGroupComments(defSources, printDefGroupComments)

    val headerLines = List(
      s"package $eventPropDefsPackagePath",
      "",
      keyTypeImport(keyKind),
      scalaJsDomImport,
      ""
    ) ++ standardTraitCommentLines.map("// " + _)

    new EventPropsTraitGenerator(
      defs = defs,
      defGroupComments = defGroupComments,
      headerLines = headerLines,
      traitCommentLines = traitCommentLines,
      traitModifiers = traitModifiers,
      traitName = s"${traitName}(using cats.effect.kernel.Async[F])",
      traitExtends = traitExtends,
      traitThisType = traitThisType,
      defType = _ => defType,
      keyKind = keyKind,
      keyImplName = _ => keyImplName,
      baseImplDefComments = Nil,
      baseImplDef = Nil,
      outputImplDefs = true,
      format = format
    ) {

      override def impl(keyDef: EventPropDef): String = {
        List[String](
          "EventProp(",
          repr(keyDef.domName),
          ", ",
          s"e => fs2.dom.${keyDef.javascriptEventType}(e.asInstanceOf[dom.${keyDef.javascriptEventType}])",
          ")"
        ).mkString
      }

    }.printTrait().getOutput()
  }

}
