package calico.html.codegen

import com.raquo.domtypes.codegen.DefType.LazyVal
import com.raquo.domtypes.codegen.{
  CanonicalCache,
  CanonicalDefGroups,
  CanonicalGenerator,
  CodeFormatting,
  SourceRepr
}
import com.raquo.domtypes.codegen.DefType
import com.raquo.domtypes.codegen.generators.AttrsTraitGenerator
import com.raquo.domtypes.codegen.generators.EventPropsTraitGenerator
import com.raquo.domtypes.codegen.generators.PropsTraitGenerator
import com.raquo.domtypes.codegen.generators.TagsTraitGenerator
import com.raquo.domtypes.common
import com.raquo.domtypes.common.TagType
import com.raquo.domtypes.common.{HtmlTagType, SvgTagType}
import com.raquo.domtypes.defs.styles.StyleTraitDefs

object DomDefsGenerator {

  private val cache = new CanonicalCache("project")

  def cachedGenerate(): Unit = {
    cache.triggerIfCacheKeyUpdated(
      metaProject.BuildInfo.scalaDomTypesVersion,
      forceOnEverySnapshot = true
    )(_ => generate())
  }

  def generate(): Unit = {
    val defGroups = new CanonicalDefGroups()

    // -- HTML tags --

    {
      val traitName = "HtmlTags"
      val traitNameWithParams = s"$traitName[F[_], T[_ <: HtmlElement[F]]]"

      val fileContent = CalicoGenerator.generateTagsTrait(
        tagType = HtmlTagType,
        // TODO introduce HTMLDialogElement to fs2-dom
        defGroups = defGroups.htmlTagsDefGroups.map {
          case (key, list) => (key, list.filter(_.javascriptElementType != "HTMLDialogElement"))
        },
        printDefGroupComments = true,
        traitCommentLines = Nil,
        traitName = traitNameWithParams,
        keyKind = "T",
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

      CalicoGenerator.writeToFile(
        packagePath = CalicoGenerator.tagDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- SVG tags --

    // {
    // val traitName = "SvgTags"

    // val fileContent = CalicoGenerator.generateTagsTrait(
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

    // CalicoGenerator.writeToFile(
    // packagePath = CalicoGenerator.tagDefsPackagePath,
    // fileName = traitName,
    // fileContent = fileContent
    // )
    // }

    // -- HTML attributes --

    {
      val traitName = "HtmlAttrs"
      val traitNameWithParams = s"$traitName[F[_]]"

      val fileContent = CalicoGenerator.generateAttrsTrait(
        defGroups = defGroups.htmlAttrDefGroups.map {
          case (key, vals) =>
            (key, vals.map(attr => attr.copy(scalaValueType = "F, " + attr.scalaValueType)))
        },
        printDefGroupComments = false,
        traitCommentLines = Nil,
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

      CalicoGenerator.writeToFile(
        packagePath = CalicoGenerator.attrDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- SVG attributes --

    // {
    // val traitName = "SvgAttrs"

    // val fileContent = CalicoGenerator.generateAttrsTrait(
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

    // CalicoGenerator.writeToFile(
    // packagePath = CalicoGenerator.attrDefsPackagePath,
    // fileName = traitName,
    // fileContent = fileContent
    // )
    // }

    // -- ARIA attributes --

    {
      val traitName = "AriaAttrs"
      val traitNameWithParams = s"$traitName[F[_]]"

      def transformAttrDomName(ariaAttrName: String): String = {
        if (ariaAttrName.startsWith("aria-")) {
          ariaAttrName.substring(5)
        } else {
          throw new Exception(s"Aria attribute does not start with `aria-`: $ariaAttrName")
        }
      }

      val fileContent = CalicoGenerator.generateAttrsTrait(
        defGroups = defGroups.ariaAttrDefGroups.map {
          case (key, vals) =>
            (key, vals.map(attr => attr.copy(scalaValueType = "F, " + attr.scalaValueType)))
        },
        printDefGroupComments = false,
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

      CalicoGenerator.writeToFile(
        packagePath = CalicoGenerator.attrDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- HTML props --

    {
      val traitName = "HtmlProps"
      val traitNameWithParams = s"$traitName[F[_]]"

      val fileContent = CalicoGenerator.generatePropsTrait(
        defGroups = defGroups.propDefGroups.map {
          case (key, vals) =>
            (key, vals.map(attr => attr.copy(scalaValueType = "F, " + attr.scalaValueType)))
        },
        printDefGroupComments = true,
        traitCommentLines = Nil,
        traitName = traitNameWithParams,
        keyKind = "HtmlProp",
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
        baseImplName = "htmlProp",
        defType = LazyVal
      )

      CalicoGenerator.writeToFile(
        packagePath = CalicoGenerator.propDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- Event props --

    {
      val baseTraitName = "GlobalEventProps"
      val baseTraitNameWithParams = s"$baseTraitName[F[_]]"

      val subTraits = List(
        ("WindowEventProps", "WindowEventProps[F[_]]", defGroups.windowEventPropDefGroups),
        ("DocumentEventProps", "DocumentEventProps[F[_]]", defGroups.documentEventPropDefGroups)
      )

      {
        val fileContent = CalicoGenerator.generateEventPropsTrait(
          defSources = defGroups.globalEventPropDefGroups.map {
            case (key, vals) =>
              (
                key,
                vals.map(attr => attr.copy(scalaJsEventType = "F, " + attr.scalaJsEventType)))
          },
          printDefGroupComments = true,
          traitCommentLines = Nil,
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

        CalicoGenerator.writeToFile(
          packagePath = CalicoGenerator.eventPropDefsPackagePath,
          fileName = baseTraitName,
          fileContent = fileContent
        )
      }

      subTraits.foreach {
        case (traitName, traitNameWithParams, eventPropsDefGroups) =>
          val fileContent = CalicoGenerator.generateEventPropsTrait(
            defSources = eventPropsDefGroups.map {
              case (key, vals) =>
                (
                  key,
                  vals.map(attr => attr.copy(scalaJsEventType = "F, " + attr.scalaJsEventType)))
            },
            printDefGroupComments = true,
            traitCommentLines = List(eventPropsDefGroups.head._1),
            traitName = traitNameWithParams,
            traitExtends = Nil,
            traitThisType = Some(baseTraitName + "[F]"),
            baseImplDefComments = Nil,
            outputBaseImpl = false,
            keyKind = "EventProp",
            keyImplName = "eventProp",
            defType = LazyVal
          )

          CalicoGenerator.writeToFile(
            packagePath = CalicoGenerator.eventPropDefsPackagePath,
            fileName = traitName,
            fileContent = fileContent
          )
      }
    }

    // -- Style props --

    // {
    // val traitName = "StyleProps"

    // val fileContent = CalicoGenerator.generateStylePropsTrait(
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

    // CalicoGenerator.writeToFile(
    // packagePath = CalicoGenerator.stylePropDefsPackagePath,
    // fileName = traitName,
    // fileContent = fileContent
    // )
    // }

    // -- Style keyword traits

    // {
    // StyleTraitDefs.defs.foreach { styleTrait =>
    // val fileContent = CalicoGenerator.generateStyleKeywordsTrait(
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

    // CalicoGenerator.writeToFile(
    // packagePath = CalicoGenerator.styleTraitsPackagePath(),
    // fileName = styleTrait.scalaName.replace("[_]", ""),
    // fileContent = fileContent
    // )
    // }
    // }
  }
}
