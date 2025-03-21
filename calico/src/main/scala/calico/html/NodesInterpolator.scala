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
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.dom.HtmlElement

import scala.quoted.*

final case class NodesInterpolator(
    parts: List[Either[String, Any]]
)

// Macro implementation for nodes string interpolator.
inline def processNodes(sc: StringContext, args: Any*): NodesInterpolator = ${
  processNodesImpl('sc, 'args)
}

private def processNodesImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(
    using Quotes): Expr[NodesInterpolator] =

  '{
    val stringContext = $sc
    val arguments = $args
    val parts = stringContext.parts

    val resultParts = List.newBuilder[Either[String, Any]]

    if parts.head.nonEmpty then resultParts += Left(parts.head)

    for i <- 0 until arguments.length do
      val arg = arguments(i)
      val nextPart = if i + 1 < parts.length then parts(i + 1) else ""

      // Add value directly without type info
      resultParts += Right(arg)

      // Add next string part if non-empty
      if nextPart.nonEmpty then resultParts += Left(nextPart)

    NodesInterpolator(resultParts.result())
  }

extension (sc: StringContext)

// String interpolator for node content.

  inline def nodes(inline args: Any*): NodesInterpolator =
    processNodes(sc, args*)

def nodes(value: Any): NodesInterpolator =
  NodesInterpolator(List(Right(value)))

/**
 * Modifier for NodesInterpolator
 */
given nodesInterpolatorModifier[E <: HtmlElement[IO]]: Modifier[IO, E, NodesInterpolator] =
  new Modifier[IO, E, NodesInterpolator]:
    def modify(interpolator: NodesInterpolator, element: E): Resource[IO, Unit] =
      interpolator
        .parts
        .foldLeft(Resource.pure[IO, Unit](())): (res, part) =>
          res.flatMap: _ =>
            part match
              case Left(text) =>
                // Text nodes handled by text modifier
                summon[Modifier[IO, E, String]].modify(text, element)
              case Right(value) =>
                // Handle value based on its type
                handleValue(value, element)

/**
 * Text content modifier that creates text nodes.
 */
given stringModifier[E <: HtmlElement[IO]]: Modifier[IO, E, String] =
  new Modifier[IO, E, String]:
    def modify(text: String, element: E): Resource[IO, Unit] =
      Resource.eval(IO {
        val textNode = org.scalajs.dom.document.createTextNode(text)
        element.asInstanceOf[org.scalajs.dom.Element].appendChild(textNode)
        ()
      })

/**
 * Signal modifier for reactive values.
 */
given signalModifier[E <: HtmlElement[IO], A]: Modifier[IO, E, Signal[IO, A]] =
  new Modifier[IO, E, Signal[IO, A]]:
    def modify(signal: Signal[IO, A], element: E): Resource[IO, Unit] =
      Resource
        .eval(IO {
          val textNode = org.scalajs.dom.document.createTextNode("")
          element.asInstanceOf[org.scalajs.dom.Element].appendChild(textNode)
          textNode
        })
        .flatMap { textNode =>
          signal
            .discrete
            .foreach { value => IO { textNode.textContent = value.toString } }
            .compile
            .drain
            .background
            .void
        }

/**
 * Common primitive type modifiers.
 */
given intModifier[E <: HtmlElement[IO]]: Modifier[IO, E, Int] =
  new Modifier[IO, E, Int]:
    def modify(value: Int, element: E): Resource[IO, Unit] =
      summon[Modifier[IO, E, String]].modify(value.toString, element)

given doubleModifier[E <: HtmlElement[IO]]: Modifier[IO, E, Double] =
  new Modifier[IO, E, Double]:
    def modify(value: Double, element: E): Resource[IO, Unit] =
      summon[Modifier[IO, E, String]].modify(value.toString, element)

given booleanModifier[E <: HtmlElement[IO]]: Modifier[IO, E, Boolean] =
  new Modifier[IO, E, Boolean]:
    def modify(value: Boolean, element: E): Resource[IO, Unit] =
      summon[Modifier[IO, E, String]].modify(value.toString, element)

/**
 * Helper method to handle different types of values using the type class pattern.
 */
private def handleValue(value: Any, element: HtmlElement[IO]): Resource[IO, Unit] =
  if value == null then summon[Modifier[IO, HtmlElement[IO], String]].modify("null", element)
  else
    // Try type-based approach
    val stringRes =
      if value.isInstanceOf[String] then
        Some(
          summon[Modifier[IO, HtmlElement[IO], String]]
            .modify(value.asInstanceOf[String], element))
      else None

    val intRes =
      if value.isInstanceOf[Int] then
        Some(
          summon[Modifier[IO, HtmlElement[IO], Int]].modify(value.asInstanceOf[Int], element))
      else None

    val doubleRes =
      if value.isInstanceOf[Double] then
        Some(
          summon[Modifier[IO, HtmlElement[IO], Double]]
            .modify(value.asInstanceOf[Double], element))
      else None

    val boolRes =
      if value.isInstanceOf[Boolean] then
        Some(
          summon[Modifier[IO, HtmlElement[IO], Boolean]]
            .modify(value.asInstanceOf[Boolean], element))
      else None

    // Try to find an appropriate modifier using direct type checks
    stringRes orElse
      intRes orElse
      doubleRes orElse
      boolRes orElse
      tryModifySignal(value, element) getOrElse
      // Fallback for unknown types
      summon[Modifier[IO, HtmlElement[IO], String]].modify(value.toString, element)

/**
 * Helper method specifically for Signal types to handle type erasure.
 */
private def tryModifySignal(value: Any, element: HtmlElement[IO]): Option[Resource[IO, Unit]] =
  if value.isInstanceOf[Signal[?, ?]] then
    val m = summon[Modifier[IO, HtmlElement[IO], Signal[IO, Any]]]
    Some(m.modify(value.asInstanceOf[Signal[IO, Any]], element))
  else None
