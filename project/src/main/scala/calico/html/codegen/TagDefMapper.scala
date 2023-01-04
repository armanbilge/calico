package calico.html.codegen

import com.raquo.domtypes.common.TagDef

object TagDefMapper {

  def extractFs2DomElementType(tagDef: TagDef): String =
    tagDef.javascriptElementType match {
      case "HTMLHtmlElement" =>
        "HtmlElement[F]"
      case "HTMLHRElement" =>
        "HtmlHrElement[F]"
      case "HTMLLIElement" =>
        "HtmlLiElement[F]"
      case "HTMLBRElement" =>
        "HtmlBrElement[F]"
      case s =>
        s"${s.replaceFirst("HTML", "Html")}[F]"
    }
}
