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

import scala.util.Try

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

    // Create text nodes from static parts
    val textParts = sc.parts.map(part => textNode[F, El](part))

    // Convert arguments to modifiers - explicitly specify the type parameter El
    val argMods = args.map(arg => processArgument[F, El](arg))

    // Interleave text parts and arguments
    val combined = interleavePartsAndArgs[El](textParts, argMods.toList)

    // Combine all modifiers by chaining them
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

  // Process an argument and convert it to a modifier
  private def processArgument[F[_], El <: HtmlElement[F]](arg: Any)(
      using F: Concurrent[F]): Modifier[F, El, Any] = {
    // Return null text node immediately
    arg match {
      case null => textNode[F, El]("null")
      case _ =>
        // Try all possible transformations in order
        val options = List[Option[Modifier[F, El, Any]]](
          processSignal[F, El](arg),
          processModifier[F, El](arg),
          processProduct[F, El](arg),
          processEffect[F, El](arg)
        )

        // Take the first successful transformation, or use toString as fallback
        options.collectFirst { case Some(mod) => mod }.getOrElse(textNode[F, El](arg.toString))
    }
  }

  // Process Signal types
  private def processSignal[F[_], El <: HtmlElement[F]](arg: Any)(
      using F: Concurrent[F]): Option[Modifier[F, El, Any]] = {
    // Avoid pattern matching on Any by using the class's canonical name
    val isSignal = arg.getClass.getName.contains("fs2.concurrent.Signal")

    isSignal match {
      case true =>
        // First try to process as Signal[F, String]
        Try {
          val signal = arg.asInstanceOf[Signal[F, String]]
          signal.map(textNode[F, El](_)).asInstanceOf[Modifier[F, El, Any]]
        }.orElse {
          // If that fails, try as Signal[F, Any]
          Try {
            val signal = arg.asInstanceOf[Signal[F, Any]]
            signal
              .map(value => textNode[F, El](value.toString))
              .asInstanceOf[Modifier[F, El, Any]]
          }
        }.toOption
      case false => None
    }
  }

  // Process Modifier types
  private def processModifier[F[_], El <: HtmlElement[F]](
      arg: Any): Option[Modifier[F, El, Any]] = {
    // Avoid pattern matching on Any by using the class's canonical name
    val isModifier = arg.getClass.getName.contains("calico.html.Modifier")

    isModifier match {
      case true =>
        Try(arg.asInstanceOf[Modifier[F, El, Any]]).toOption
      case false => None
    }
  }

  // Process Product types (tuples, case classes)
  private def processProduct[F[_], El <: HtmlElement[F]](arg: Any)(
      using F: Concurrent[F]): Option[Modifier[F, El, Any]] = {
    // Use isInstance with specific matching against Product.class
    val isProduct = classOf[Product].isAssignableFrom(arg.getClass)

    isProduct match {
      case true =>
        val product = arg.asInstanceOf[Product]
        Some(textNode[F, El](formatProduct(product)))
      case false => None
    }
  }

  // Format products without pattern matching on Any
  private def formatProduct(product: Product): String = {
    val elements = new scala.collection.mutable.ArrayBuffer[String]()

    for (i <- 0 until product.productArity) {
      val elem = product.productElement(i)
      val formatted = elem match {
        case null => "null"
        case _ =>
          // Check if it's a Signal without pattern matching
          val isSignal = elem.getClass.getName.contains("fs2.concurrent.Signal")
          // Use match instead of if/else for -new-syntax compliance
          isSignal match {
            case true => s"Signal(${elem.hashCode})"
            case false =>
              // Check if it's a Product without pattern matching
              val isNestedProduct = classOf[Product].isAssignableFrom(elem.getClass)
              val hasElements = isNestedProduct && elem.asInstanceOf[Product].productArity > 0

              // Use match instead of if/else for -new-syntax compliance
              hasElements match {
                case true => formatProduct(elem.asInstanceOf[Product])
                case false => elem.toString
              }
          }
      }
      elements += formatted
    }

    elements.mkString("(", ", ", ")")
  }

  // Process Effect types (IO, F[_])
  private def processEffect[F[_], El <: HtmlElement[F]](arg: Any)(
      using F: Concurrent[F]): Option[Modifier[F, El, Any]] = {
    val className = arg.getClass.getName

    // Check if className contains relevant patterns that suggest it's an effect type
    val mightBeEffect = className.contains("cats.effect") || className.contains("IO")

    mightBeEffect match {
      case true =>
        Try {
          F.map(arg.asInstanceOf[F[Any]])(x => textNode[F, El](x.toString))
            .asInstanceOf[Modifier[F, El, Any]]
        }.toOption
      case false => None
    }
  }

  // Interleave text parts and arguments
  private def interleavePartsAndArgs[El <: HtmlElement[F]](
      parts: Seq[Modifier[F, El, Any]],
      args: List[Modifier[F, El, Any]]
  ): List[Modifier[F, El, Any]] = {
    val result = scala.collection.mutable.ListBuffer[Modifier[F, El, Any]]()

    // Add the first text part
    result += parts.head

    // Add interleaved parts and args
    (0 until args.length).foreach { i =>
      result += args(i)
      result += parts(i + 1)
    }

    result.toList
  }

  // Helper method to create text nodes
  private def textNode[F[_], El <: HtmlElement[F]](content: String)(
      using F: Concurrent[F]): Modifier[F, El, Any] = {
    new Modifier[F, El, Any] {
      def modify(a: Any, e: El): Resource[F, Unit] = {
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
