package calico.html.codegen

import com.raquo.domtypes.common.{AttrDef, HtmlTagType}
import com.raquo.domtypes.codegen.CanonicalDefGroups

object CustomCanonicalDefGroups extends CanonicalDefGroups {
  override val htmlAttrDefGroups: List[(String, List[AttrDef])] = {

    val base: Map[String, Seq[AttrDef]] = (new CanonicalDefGroups).htmlAttrDefGroups.toMap

    val updated: Map[String, Seq[AttrDef]] = base ++ Map(
      "value" -> Seq(
        AttrDef(
          tagType = HtmlTagType,      
          scalaName = "value",
          scalaAliases = List("value"),
          domName = "value",
          namespace = None,
          scalaValueType = "String",
          codec = "StringAsIsCodec",
          commentLines = Nil,
          docUrls = Nil
        )
      )
    )
   
    updated.toList.map { case (k, vs) => (k, vs.toList) }
  }
}
