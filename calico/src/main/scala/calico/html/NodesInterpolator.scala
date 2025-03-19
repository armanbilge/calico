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

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.functor.*
import fs2.concurrent.Signal
import fs2.dom.HtmlElement

import scala.compiletime.constValue
import scala.compiletime.erasedValue
import scala.compiletime.summonFrom
import scala.quoted.*

// Type-level function for interspersing strings between tuple elements
type IntersperseStrings[T <: Tuple] <: Tuple = T match
  case EmptyTuple => String *: EmptyTuple
  case (t *: ts) => String *: t *: IntersperseStrings[ts]

// Value-level function for interspersing strings between tuple elements
inline def intersperseStrings[T <: Tuple](t: T, strings: Seq[String]): IntersperseStrings[T] =
  inline erasedValue[T] match
    case _: EmptyTuple => strings.head *: EmptyTuple
    case _: (head *: tail) =>
      inline t match
        case v: (`head` *: `tail`) =>
          val (h *: t) = v
          strings.head *: h *: intersperseStrings(t, strings.tail)

// Define a wrapper class to hide implementation details
final case class NodesInterpolator(contents: List[NodeContent])

// Define the NodeContent trait and implementations
sealed trait NodeContent
case class StaticContent(text: String) extends NodeContent
case class DynamicContent[A](signal: Signal[IO, A]) extends NodeContent

// Helper method to detect any Signal implementation
private def isSignal(obj: Any): Boolean =
  obj != null && obj.isInstanceOf[Signal[?, ?]]

// Macro implementation for nodes string interpolator
inline def processNodes(sc: StringContext, args: Any*): NodesInterpolator = ${
  processNodesImpl('sc, 'args)
}

private def processNodesImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(
    using Quotes): Expr[NodesInterpolator] =
  import quotes.reflect.*

  // Try to do compile-time processing when possible
  try
    sc match
      case '{ StringContext(${ Varargs(literalParts) }*) }
          if literalParts.forall(_.value.isDefined) =>
        // Extract string parts if they are all literal strings
        val stringParts = literalParts.map(_.valueOrAbort).toList

        args match
          case '{ Seq(${ Varargs(argList) }*) } =>
            // We have both literal strings and extractable arguments
            val argExprs = argList.toList
            val contentExprs = buildContentExpressions(stringParts, argExprs)
            '{ NodesInterpolator(${ Expr.ofList(contentExprs) }) }

          case _ =>
            // Arguments aren't extractable at compile time
            fallbackToRuntime(sc, args)

      case _ =>
        // String context isn't all literals
        fallbackToRuntime(sc, args)
  catch
    case _: Exception =>
      // Any error during compile-time analysis, fallback to runtime
      fallbackToRuntime(sc, args)

// Runtime fallback implementation
private def fallbackToRuntime(sc: Expr[StringContext], args: Expr[Seq[Any]])(
    using Quotes): Expr[NodesInterpolator] =
  '{
    val parts = $sc.parts.map(StringContext.processEscapes)
    val arguments = $args
    val result = List.newBuilder[NodeContent]

    // Add first static part if non-empty
    parts.head match
      case p if p.nonEmpty => result += StaticContent(p)
      case _ => ()

    // Add alternating dynamic and static parts
    for (i <- 0 until arguments.length)
      val arg = arguments(i)
      val nextPart = parts.applyOrElse(i + 1, (_: Int) => "")

      // Use match instead of if
      arg match
        case a if isSignal(a) =>
          result += DynamicContent(a.asInstanceOf[Signal[IO, Any]])
        case other =>
          result += StaticContent(other.toString)

      // Add static content if non-empty
      nextPart match
        case p if p.nonEmpty => result += StaticContent(p)
        case _ => ()

    NodesInterpolator(result.result())
  }

// Helper method to build content expressions at compile time
private def buildContentExpressions(
    stringParts: List[String],
    argExprs: List[Expr[Any]]
)(using Quotes): List[Expr[NodeContent]] =
  import quotes.reflect.*

  val result = scala.collection.mutable.ListBuffer.empty[Expr[NodeContent]]

  // Add first string part if non-empty
  stringParts.head match
    case p if p.nonEmpty => result += '{ StaticContent(${ Expr(p) }) }
    case _ => ()

  // Process arguments and remaining string parts
  for i <- 0 until argExprs.length do
    val argExpr = argExprs(i)
    val nextPart = stringParts.applyOrElse(i + 1, (_: Int) => "")

    // Check if the argument is a Signal at compile time
    val argType = argExpr.asTerm.tpe.widen.dealias

    // Use match instead of if for Scala 3 new syntax
    argType <:< TypeRepr.of[Signal[IO, ?]] match
      case true =>
        // Signal type
        result += '{ DynamicContent($argExpr.asInstanceOf[Signal[IO, Any]]) }
      case false =>
        // Any other type - convert to string
        result += '{ StaticContent($argExpr.toString) }

    // Add next string part if non-empty
    nextPart match
      case p if p.nonEmpty => result += '{ StaticContent(${ Expr(p) }) }
      case _ => ()

  result.toList

extension (sc: StringContext)
  // New compile-time string interpolator
  inline def nodes(inline args: Any*): NodesInterpolator =
    processNodes(sc, args*)

  // Keep the tuple-based version for future use
  inline def nodesT[M <: Tuple, E <: HtmlElement[IO]](
      arg: M
  )(
      using Modifier[IO, E, M]
  ): IntersperseStrings[M] =
    StringContext.checkLengths(arg.toList, sc.parts)
    intersperseStrings(arg, sc.parts.map(StringContext.processEscapes))

// Use this modifier for NodesInterpolator
given nodesInterpolatorModifier[E <: HtmlElement[IO]]: Modifier[IO, E, NodesInterpolator] =
  new Modifier[IO, E, NodesInterpolator]:
    def modify(interpolator: NodesInterpolator, element: E) =
      interpolator
        .contents
        .foldLeft(Resource.pure[IO, Unit](())): (res, content) =>
          res.flatMap: _ =>
            content match
              case StaticContent(text) =>
                Resource.eval(IO {
                  val textNode = org.scalajs.dom.document.createTextNode(text)
                  element.asInstanceOf[org.scalajs.dom.Element].appendChild(textNode)
                  ()
                })

              case DynamicContent(signal) =>
                // Properly suspend side effects in a single Resource.eval
                Resource
                  .eval(IO {
                    val span = org.scalajs.dom.document.createElement("span")
                    element.asInstanceOf[org.scalajs.dom.Element].appendChild(span)
                    span
                  })
                  .flatMap: span =>
                    signal
                      .discrete
                      .foreach: value =>
                        IO {
                          span.textContent = value.toString
                        }
                      .compile
                      .drain
                      .background
                      .void
