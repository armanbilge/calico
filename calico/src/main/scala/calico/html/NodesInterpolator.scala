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

package calico.html

import cats.effect.Concurrent
import cats.effect.kernel.Resource
import fs2.concurrent.Signal
import fs2.dom.HtmlElement
import org.scalajs.dom

extension (sc: StringContext) {
  def nodes[F[_]]: NodesInterpolator[F] = new NodesInterpolator(sc)

  inline def nodes[F[_], El <: HtmlElement[F]](using F: Concurrent[F])(
      args: Any*): Modifier[F, El, Any] = {
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
      case null => textNode[F, El]("null")

      // Using type test pattern instead of isInstanceOf
      case arg: Signal[F, String] =>
        arg.map(textNode[F, El](_)).asInstanceOf[Modifier[F, El, Any]]

      case arg: Signal[F, ?] =>
        arg.map(value => textNode[F, El](value.toString)).asInstanceOf[Modifier[F, El, Any]]

      case arg: F[?] =>
        // For F[] types, we need to map to a text node
        F.map(arg.asInstanceOf[F[Any]])(v => textNode[F, El](v.toString))
          .asInstanceOf[Modifier[F, El, Any]]

      case arg: Modifier[?, ?, ?] =>
        arg.asInstanceOf[Modifier[F, El, Any]]

      case arg: Product =>
        textNode[F, El](formatTuple(arg))

      case other =>
        textNode[F, El](other.toString)
    }.toList

    // Interleave text parts and arguments
    val combined = interleavePartsAndArgs(textParts, argMods)

    // Combine all modifiers by chaining them with andThen
    combined.reduce { (mod1, mod2) =>
      new Modifier[F, El, Any] {
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
      tuple.productElement(i) match {
        case null => "null"
        case elem: Signal[?, ?] => s"Signal(${elem.hashCode})"
        case elem: Product if elem.productArity > 0 => formatTuple(elem)
        case elem => elem.toString
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
  private def textNode[F[_], El <: HtmlElement[F]](content: String)(
      using F: Concurrent[F]): Modifier[F, El, Any] = {
    new Modifier[F, El, Any] {
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
