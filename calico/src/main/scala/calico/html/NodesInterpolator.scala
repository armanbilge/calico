// FILE: src/main/scala/calico/html/NodesInterpolator.scala
package calico.html

import cats.effect.Concurrent
import cats.effect.kernel.Resource
import fs2.dom.HtmlElement
import fs2.concurrent.Signal
import org.scalajs.dom

extension (sc: StringContext) {
  def nodes[F[_]]: NodesInterpolator[F] = new NodesInterpolator(sc)
  
  inline def nodes[F[_], El <: HtmlElement[F]](using F: Concurrent[F])(args: Any*): Modifier[F, El, Any] = {
    val interpolator = new NodesInterpolator[F](sc)
    interpolator.apply[El](args*)
  }
}

class NodesInterpolator[F[_]](private val sc: StringContext) extends AnyVal {
  def apply[El <: HtmlElement[F]](args: Any*)(using F: Concurrent[F]): Modifier[F, El, Any] = {
    require(
      args.length == sc.parts.length - 1, 
      s"wrong number of arguments (${args.length}) for interpolated string with ${sc.parts.length} parts"
    )
    
    // Create text nodes from static parts - directly create textNode modifiers
    val textParts = sc.parts.map(part => textNode[F, El](part))

    // Convert arguments to modifiers based on their actual type
    val argMods = args.map {
      case s: Signal[F, String] @unchecked => 
        s.map(textNode[F, El](_)).asInstanceOf[Modifier[F, El, Any]]
      case s: Signal[F, ?] @unchecked => 
        s.map(value => textNode[F, El](value.toString)).asInstanceOf[Modifier[F, El, Any]]
      case f: F[String] @unchecked => 
        F.map(f)(str => textNode[F, El](str)).asInstanceOf[Modifier[F, El, Any]]
      case m: Modifier[F, El, ?] @unchecked => 
        m.asInstanceOf[Modifier[F, El, Any]]
      // Special case for tuples - convert to string representation directly
      case tuple: Product @unchecked => 
        textNode[F, El](formatTuple(tuple)).asInstanceOf[Modifier[F, El, Any]]
      case other => 
        textNode[F, El](other.toString).asInstanceOf[Modifier[F, El, Any]]
    }

    // Interleave text parts and arguments
    val combined = interleavePartsAndArgs(textParts, argMods.toList)

    // Combine all modifiers by chaining them with andThen
    combined.reduce { (mod1, mod2) => 
      new Modifier[F, El, Any] {
        // Match the exact signature from the error message
        def modify(a: Any, e: El): Resource[F, Unit] = {
          for {
            _ <- mod1.modify(a, e) 
            _ <- mod2.modify(a, e)
          } yield ()
        }
      }
    }
  }
  
  // Format tuples in a more readable way
  private def formatTuple(tuple: Product): String = {
    val elements = for (i <- 0 until tuple.productArity) yield {
      val elem = tuple.productElement(i)
      elem match {
        case signal: Signal[?, ?] @unchecked => s"Signal(${signal.hashCode})"  // Don't try to toString signals
        case p: Product @unchecked if p.productArity > 0 => formatTuple(p)     // Recursively format nested tuples
        case other => other.toString
      }
    }
    
    // Format as (elem1, elem2, ...)
    elements.mkString("(", ", ", ")")
  }
  
  private def interleavePartsAndArgs[El <: HtmlElement[F]](
      parts: Seq[Modifier[F, El, Any]], 
      args: List[Modifier[F, El, Any]]
  ): List[Modifier[F, El, Any]] = {
    val result = scala.collection.mutable.ListBuffer[Modifier[F, El, Any]]()
    
    // Add the first text part
    result += parts.head
    
    // Add interleaved parts and args
    for (i <- 0 until args.length) {
      result += args(i)
      result += parts(i + 1)
    }
    
    result.toList
  }
  
  // Helper method to create text nodes using the appropriate methods for your Calico version
  private def textNode[F[_], El <: HtmlElement[F]](content: String)(using F: Concurrent[F]): Modifier[F, El, Any] = {
    new Modifier[F, El, Any] {
      // Match the exact signature from the error message
      def modify(a: Any, e: El): Resource[F, Unit] = {
        // Create a text node and append it directly to the element
        Resource.eval(
          F.pure {
            val textNode = dom.document.createTextNode(content)
            e.asInstanceOf[dom.Element].appendChild(textNode)
            ()
          }
        )
      }
    }
  }
}
