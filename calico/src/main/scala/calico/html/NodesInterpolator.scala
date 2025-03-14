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

import scala.annotation.nowarn

// Type-level function for interspersing strings between tuple elements
type IntersperseStrings[T <: Tuple] <: Tuple =
  T match {
    case EmptyTuple => String *: EmptyTuple
    case (t *: ts) => String *: t *: IntersperseStrings[ts]
  }

// Value-level function for interspersing strings between tuple elements
inline def intersperseStrings[T <: Tuple](t: T, strings: Seq[String]): IntersperseStrings[T] =
  inline scala.compiletime.erasedValue[T] match {
    case _: EmptyTuple => strings.head *: EmptyTuple
    case _: (head *: tail) =>
      inline t match {
        case v: (`head` *: `tail`) =>
          val (h *: t) = v
          strings.head *: h *: intersperseStrings(t, strings.tail)
      }
  }

// Helper method to detect any Signal implementation
private def isSignal(obj: Any): Boolean =
  obj != null && obj.isInstanceOf[Signal[?, ?]]

// Define a wrapper class to hide implementation details
final case class NodesInterpolator(contents: List[NodeContent])

extension (sc: StringContext) {
  // String interpolator with variadic arguments (standard string interpolator style)
  @nowarn("msg=pattern selector should be an instance of Matchable")
  def nodes(args: Any*): NodesInterpolator = {
    val parts = sc.parts.map(StringContext.processEscapes)
    val result = List.newBuilder[NodeContent]

    // Add first static part if non-empty
    parts.head match
      case p if p.nonEmpty => result += StaticContent(p)
      case _ => ()

    // Add alternating dynamic and static parts
    for (i <- 0 until args.length) {
      val arg = args(i)
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
    }

    NodesInterpolator(result.result())
  }

  // Keep the tuple-based version for future use
  inline def nodesT[M <: Tuple, E <: HtmlElement[IO]](
      arg: M
  )(
      using Modifier[IO, E, M]
  ): IntersperseStrings[M] = {
    StringContext.checkLengths(arg.toList, sc.parts)
    intersperseStrings(arg, sc.parts.map(StringContext.processEscapes))
  }
}

// Define the NodeContent trait and implementations
sealed trait NodeContent
case class StaticContent(text: String) extends NodeContent
case class DynamicContent[A](signal: Signal[IO, A]) extends NodeContent

// Use this modifier for NodesInterpolator
given nodesInterpolatorModifier[E <: HtmlElement[IO]]: Modifier[IO, E, NodesInterpolator] =
  new Modifier[IO, E, NodesInterpolator] {
    def modify(interpolator: NodesInterpolator, element: E) = {
      interpolator.contents.foldLeft(Resource.pure[IO, Unit](())) { (res, content) =>
        res.flatMap { _ =>
          content match {
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
                .flatMap { span =>
                  val stream = signal
                    .discrete
                    .foreach { value =>
                      IO {
                        span.textContent = value.toString
                      }
                    }
                    .compile
                    .drain

                  stream.background.as(())
                }
          }
        }
      }
    }
  }
