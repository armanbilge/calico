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
    parts: List[Either[String, NodeValue]]
)

final case class NodeValue(value: Any, typeName: String)

// Macro implementation for nodes string interpolator.
inline def processNodes(sc: StringContext, args: Any*): NodesInterpolator = ${
  processNodesImpl('sc, 'args)
}

private def processNodesImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(
    using q: Quotes
): Expr[NodesInterpolator] =
  import q.reflect.*

  // Helper to check if an expression is complex (requiring runtime handling)
  def isComplexExpression(arg: Expr[Any]): Boolean =
    // More precise detection of complex expressions
    val source = arg.asTerm.pos.sourceCode.getOrElse("")
    source.contains("=>") ||
    source.contains("case ") ||
    source.contains("match") ||
    (source.contains(".map(") || source.contains(".map ")) ||
    source.contains(".flatMap") ||
    source.contains(".getOrElse") ||
    (source.contains("if ") && source.contains(" else "))

  // Check if we have complex arguments that need runtime handling
  val hasComplexArgs = args match
    case '{ Seq(${ Varargs(argList) }*) } =>
      argList.exists(isComplexExpression)
    case _ => true

  if hasComplexArgs then
    // For complex expressions, generate runtime code

    '{
      val stringContext = $sc
      val arguments = $args
      val parts = stringContext.parts

      val resultParts = List.newBuilder[Either[String, NodeValue]]

      // Add first string part if non-empty
      if parts.head.nonEmpty then resultParts += Left(parts.head)

      // Process arguments and remaining parts
      for i <- 0 until arguments.length do
        val arg = arguments(i)
        val nextPart = if i + 1 < parts.length then parts(i + 1) else ""

        // Add value with its type info for later modifier lookup
        resultParts += Right(NodeValue(arg, arg.getClass.getName))

        // Add next string part if non-empty
        if nextPart.nonEmpty then resultParts += Left(nextPart)

      NodesInterpolator(resultParts.result())
    }
  else
    // For simple expressions, use compile-time checking
    try
      // Extract string parts and arguments
      val stringParts = sc match
        case '{ StringContext(${ Varargs(literalParts) }*) } =>
          literalParts.map(_.valueOrAbort).toList
        case _ =>
          report.errorAndAbort("Expected StringContext with literal parts")

      val argExprs = args match
        case '{ Seq(${ Varargs(argList) }*) } =>
          argList.toList
        case _ =>
          report.errorAndAbort("Could not extract argument list")

      // Build a list of static strings and dynamic values
      val resultParts =
        scala.collection.mutable.ListBuffer.empty[Expr[Either[String, NodeValue]]]

      // Add first string part if non-empty
      stringParts.head match
        case part if part.nonEmpty => resultParts += '{ Left(${ Expr(part) }) }
        case _ => ()

      // Process arguments and remaining string parts
      for i <- 0 until argExprs.length do
        val argExpr = argExprs(i)
        val nextPart = stringParts.applyOrElse(i + 1, (_: Int) => "")

        // Get the argument's type for compile-time checking
        val argType = argExpr.asTerm.tpe.widen

        // Check if a modifier exists for this type at compile time
        // Special cases for signals and strings (common types)
        val typeStr = argType.show
        if typeStr.startsWith("fs2.concurrent.Signal[") ||
          typeStr == "String" ||
          typeStr == "Int" ||
          typeStr == "Double" ||
          typeStr == "Boolean" then
          // Common types we know are handled
          ()
        else
          // For other types, try to find a modifier at compile time
          val modifierType =
            TypeRepr.of[Modifier[IO, HtmlElement[IO], ?]].appliedTo(List(argType))

          Implicits.search(modifierType) match
            case iss: ImplicitSearchSuccess =>
              // Found a modifier, continue
              ()
            case _: ImplicitSearchFailure =>
              // No modifier found, abort compilation
              report.errorAndAbort(
                s"No Modifier found for type ${argType.show} in nodes interpolator. " +
                  s"Either provide a Modifier instance or use a more basic type."
              )

        // Add this value with its type information
        val typeName = argType.show
        resultParts += '{ Right(NodeValue($argExpr, ${ Expr(typeName) })) }

        // Add next string part if non-empty
        nextPart match
          case part if part.nonEmpty => resultParts += '{ Left(${ Expr(part) }) }
          case _ => ()

      '{ NodesInterpolator(${ Expr.ofList(resultParts.toList) }) }
    catch
      // If there's any error during compile-time checking,
      // report it instead of falling back to runtime
      case e: Exception =>
        report.errorAndAbort(
          s"Error during compile-time checking of nodes interpolator: ${e.getMessage}"
        )

extension (sc: StringContext)
  /**
   * String interpolator for node content.
   *
   * Example: `div(nodes"Hello, $name!")`
   *
   * For simple expressions, the compiler checks that appropriate modifiers exist. For complex
   * expressions (map/flatMap, pattern matching), handling is deferred to runtime.
   */
  inline def nodes(inline args: Any*): NodesInterpolator =
    processNodes(sc, args*)

/**
 * Modifier for NodesInterpolator that handles all the string parts and delegates to appropriate
 * modifiers for the interpolated values.
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
                // Text nodes handled by existing text modifier
                textModifier.modify(text, element)

              case Right(nodeValue) =>
                // Look up the appropriate modifier for this value based on runtime type
                findModifier(nodeValue.value).modify(nodeValue.value, element)

/**
 * Text content modifier that creates text nodes.
 */
private val textModifier: Modifier[IO, HtmlElement[IO], String] =
  new Modifier[IO, HtmlElement[IO], String]:
    def modify(text: String, element: HtmlElement[IO]): Resource[IO, Unit] =
      Resource.eval(IO {
        val textNode = org.scalajs.dom.document.createTextNode(text)
        element.asInstanceOf[org.scalajs.dom.Element].appendChild(textNode)
        ()
      })

/**
 * Find an appropriate modifier for a value based on its runtime type. Only used for complex
 * expressions that need runtime handling.
 */
private def findModifier(value: Any): Modifier[IO, HtmlElement[IO], Any] =
  // Check for Signal type without using isInstanceOf with explicit type parameters
  val isSignal = value != null && value.getClass.getName.contains("fs2.concurrent.Signal")

  if isSignal then
    // Special handling for Signal type
    new Modifier[IO, HtmlElement[IO], Any]:
      def modify(value: Any, element: HtmlElement[IO]): Resource[IO, Unit] =
        val sig = value.asInstanceOf[Signal[IO, Any]]
        Resource
          .eval(IO {
            val textNode = org.scalajs.dom.document.createTextNode("")
            element.asInstanceOf[org.scalajs.dom.Element].appendChild(textNode)
            textNode
          })
          .flatMap { textNode =>
            sig
              .discrete
              .foreach { value => IO { textNode.textContent = value.toString } }
              .compile
              .drain
              .background
              .void
          }
  else if modifierRegistry.exists(_.canModify(value)) then
    // Try to find a registered modifier for this type
    modifierRegistry.find(_.canModify(value)).get
  else
    // Default toString modifier as fallback
    // This should only be used for complex expressions where compile-time checking wasn't possible
    new Modifier[IO, HtmlElement[IO], Any]:
      def modify(value: Any, element: HtmlElement[IO]): Resource[IO, Unit] =
        textModifier.modify(value.toString, element)

// Registry of modifiers for different types
// This allows adding new modifiers for specific types without modifying existing code
private val modifierRegistry: List[Modifier[IO, HtmlElement[IO], Any]] = List(
  // Add more modifiers here as needed for specific types
)

// Type class to check if a modifier can handle a particular value
extension (modifier: Modifier[IO, HtmlElement[IO], Any])
  private def canModify(value: Any): Boolean =
    // Placeholder implementation - real implementations would check based on type
    false
