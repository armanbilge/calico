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

// Define the ArgumentType enum outside of the value class
private enum ArgumentType:
  case Signal, Modifier, Product, Effect, Other

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

    // Convert arguments to modifiers
    val argMods = args.map(arg => processArgument[F, El](arg))

    // Interleave text parts and arguments
    val combined = interleavePartsAndArgs[F, El](textParts, argMods.toList)

    // Combine all modifiers by chaining them
    combined.reduceLeft { (mod1, mod2) =>
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
    arg match {
      case null => textNode[F, El]("null")
      case _ => 
        getArgumentType(arg) match {
          case ArgumentType.Signal => processSignalByReflection[F, El](arg)
          case ArgumentType.Modifier => safeModifierCastByReflection[F, El](arg)
          case ArgumentType.Product => textNode[F, El](formatProduct(arg.asInstanceOf[Product]))
          case ArgumentType.Effect => processEffectByReflection[F, El](arg)
          case ArgumentType.Other => textNode[F, El](String.valueOf(arg))
        }
    }
  }
  
  // Determine the type of argument
  private def getArgumentType(arg: Any): ArgumentType = {
    arg match {
      case null => ArgumentType.Other
      case _ =>
        isSignalType(arg) match {
          case true => ArgumentType.Signal
          case false => 
            isModifierType(arg) match {
              case true => ArgumentType.Modifier
              case false => 
                arg.isInstanceOf[Product] match {
                  case true => ArgumentType.Product
                  case false => 
                    isEffect(arg) match {
                      case true => ArgumentType.Effect
                      case false => ArgumentType.Other
                    }
                }
            }
        }
    }
  }

  // Check if an object is a Signal by checking its class name
  private def isSignalType(obj: Any): Boolean = 
    obj != null && obj.getClass.getName.contains("fs2.concurrent.Signal")
    
  // Check if an object is a Modifier by checking its class name
  private def isModifierType(obj: Any): Boolean = 
    obj != null && obj.getClass.getName.contains("calico.html.Modifier")

  // Process a signal using reflection to avoid type pattern matching warnings
  private def processSignalByReflection[F[_], El <: HtmlElement[F]](signal: Any)(
      using F: Concurrent[F]): Modifier[F, El, Any] = {
    val typedSignal = signal.asInstanceOf[Signal[F, Any]]
    typedSignal.map { value =>
      value.isInstanceOf[String] match {
        case true => textNode[F, El](value.asInstanceOf[String])
        case false => textNode[F, El](String.valueOf(value))
      }
    }.asInstanceOf[Modifier[F, El, Any]]
  }

  // Safely cast a modifier using reflection to avoid type pattern matching warnings
  private def safeModifierCastByReflection[F[_], El <: HtmlElement[F]](modifier: Any)(
      using F: Concurrent[F]): Modifier[F, El, Any] = {
    try {
      modifier.asInstanceOf[Modifier[F, El, Any]]
    } catch {
      case _: ClassCastException =>
        textNode[F, El](s"[Type error: incompatible modifier]")
    }
  }

  // Process an effect type using reflection
  private def processEffectByReflection[F[_], El <: HtmlElement[F]](effect: Any)(
      using F: Concurrent[F]): Modifier[F, El, Any] = {
    Try(F.map(effect.asInstanceOf[F[Any]])(x => textNode[F, El](String.valueOf(x))))
      .getOrElse(textNode[F, El](effect.toString))
      .asInstanceOf[Modifier[F, El, Any]]
  }

  // Check if an object is likely an effect type
  private def isEffect(obj: Any): Boolean = {
    obj != null && {
      val className = obj.getClass.getName
      className.contains("cats.effect") || 
      className.contains("IO") ||
      className.contains("monix") ||
      className.contains("zio")
    }
  }

  // Format products safely
  private def formatProduct(product: Product): String = {
    val elements = new scala.collection.mutable.ArrayBuffer[String]()

    for (i <- 0 until product.productArity) {
      val elem = product.productElement(i)
      
      val formatted = processProductElement(elem)
      elements += formatted
    }

    elements.mkString("(", ", ", ")")
  }
  
  // Process a product element
  private def processProductElement(elem: Any): String = {
    elem match {
      case null => "null"
      case _ => 
        (elem != null && elem.getClass.getName.contains("Signal")) match {
          case true => s"Signal(${elem.hashCode})"
          case false => 
            elem.isInstanceOf[Product] match {
              case true =>
                val nestedProduct = elem.asInstanceOf[Product]
                nestedProduct.productArity > 0 match {
                  case true => formatProduct(nestedProduct)
                  case false => nestedProduct.toString
                }
              case false => String.valueOf(elem)
            }
        }
    }
  }

  // Interleave text parts and arguments
  private def interleavePartsAndArgs[F[_], El <: HtmlElement[F]](
      parts: Seq[Modifier[F, El, Any]],
      args: List[Modifier[F, El, Any]]
  ): List[Modifier[F, El, Any]] = {
    val result = scala.collection.mutable.ListBuffer[Modifier[F, El, Any]]()

    // Add the first text part if it exists
    parts.nonEmpty match {
      case true => result += parts.head
      case false => () // Do nothing
    }

    // Add interleaved parts and args
    args.zipWithIndex.foreach { case (arg, i) =>
      result += arg
      (i + 1 < parts.length) match {
        case true => result += parts(i + 1)
        case false => () // Do nothing
      }
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
