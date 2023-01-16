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

  override val codecsImport: String =
    List(
      s"import ${basePackagePath}.codecs.Codec.*",
      s"import ${basePackagePath}.codecs.AsIsCodec.*",
      s"import ${basePackagePath}.codecs.*"
    ).mkString("\n")

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
        s"protected def ${keyImplName}[$scalaJsElementTypeParam <: $baseScalaJsHtmlElementType](key: String, void: Boolean = false): ${keyKind}[$scalaJsElementTypeParam]"
      )
    } else {
      List(
        s"def ${keyImplName}[$scalaJsElementTypeParam <: $baseScalaJsSvgElementType](key: String): ${keyKind}[$scalaJsElementTypeParam] = ${keyKindConstructor(keyKind)}(key)"
      )
    }

    val headerLines = List(
      s"package $tagDefsPackagePath",
      "",
      "import calico.html.HtmlTagT",
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
      keyType = tag => keyKind + "[" + TagDefMapper.extractFs2DomElementType(tag) + "]",
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
        s"def ${baseImplName}[V](key: String, codec: Codec[V, String], namespace: Option[String]): ${keyKind}[V] = ${keyKindConstructor(keyKind)}(key, codec, namespace)"
      )
    } else {
      List(
        s"protected def ${baseImplName}[V](key: String, codec: Codec[V, String]): ${keyKind}[F, V] = ${keyKindConstructor(keyKind)}(key, codec)"
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
      transformCodecName = _ + "Codec",
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
      s"def ${baseImplName}[V, DomV](key: String, codec: Codec[V, DomV]): ${keyKind}[F, V, DomV] = ${keyKindConstructor(keyKind)}(key, codec)"
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
      transformCodecName = _ + "Codec",
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

    val baseImplDef = if (outputBaseImpl)
      List(
        s"def ${keyImplName}[Ev <: ${baseScalaJsEventType}](key: String): ${keyKind}[F, Ev] = ${keyKindConstructor(keyKind)}(key)"
      )
    else {
      Nil
    }

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
      traitName = traitName,
      traitExtends = traitExtends,
      traitThisType = traitThisType,
      defType = _ => defType,
      keyKind = keyKind,
      keyImplName = _ => keyImplName,
      baseImplDefComments = baseImplDefComments,
      baseImplDef = baseImplDef,
      outputImplDefs = true,
      format = format
    ).printTrait().getOutput()
  }

}
