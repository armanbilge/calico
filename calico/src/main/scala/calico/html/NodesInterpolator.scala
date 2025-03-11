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

import cats.effect.{IO, Resource}
import cats.syntax.all.* // Import cats syntax for *> operator
import fs2.concurrent.Signal
import fs2.dom.HtmlElement
import scala.annotation.nowarn

// A simplified wrapper that can hold either a static String or a Signal
sealed trait NodeContent
case class StaticContent(text: String) extends NodeContent
case class DynamicContent(signal: Signal[IO, Any]) extends NodeContent

extension (sc: StringContext) {
  // String interpolator for nodes - returns a list of NodeContent items
  @nowarn("msg=pattern selector should be an instance of Matchable")
  def nodes(args: Any*): List[NodeContent] = {
    val parts = sc.parts.map(StringContext.processEscapes)
    
    // Create a list of alternating static and dynamic content
    val initialStatic = List[NodeContent](StaticContent(parts.head)) // Explicitly type as NodeContent
    
    args.zip(parts.tail).foldLeft(initialStatic) { case (acc, (arg, part)) =>
      arg match {
        case signal: Signal[IO, ?] => 
          // Fix: Use ++ instead of :+ to concatenate lists
          acc ++ List[NodeContent](DynamicContent(signal.asInstanceOf[Signal[IO, Any]]), StaticContent(part))
        case other => 
          acc ++ List[NodeContent](StaticContent(other.toString + part))
      }
    }
  }
}

// Generic modifier for the NodeContent list - works with any HTML element
given nodeContentListModifier[El <: HtmlElement[IO]]: Modifier[IO, El, List[NodeContent]] with {
  def modify(contents: List[NodeContent], el: El): Resource[IO, Unit] = {
    // First, create all DOM elements with placeholders for dynamic content
    val setupResource = Resource.eval(IO {
      val idMap = scala.collection.mutable.Map.empty[String, org.scalajs.dom.Element]
      
      contents.foreach {
        case StaticContent(text) => 
          val textNode = org.scalajs.dom.document.createTextNode(text)
          el.asInstanceOf[org.scalajs.dom.Element].appendChild(textNode)
          
        case DynamicContent(signal) => 
          val spanId = s"nodes-${System.identityHashCode(signal)}"
          val span = org.scalajs.dom.document.createElement("span")
          span.setAttribute("id", spanId)
          el.asInstanceOf[org.scalajs.dom.Element].appendChild(span)
          idMap(spanId) = span
      }
      
      idMap.toMap
    })
    
    // Then, set up reactive updates for all dynamic content
    setupResource.flatMap { idMap =>
      contents.collect { case DynamicContent(signal) => signal }.foldLeft(Resource.pure[IO, Unit](())) { 
        (resource, signal) => 
          val spanId = s"nodes-${System.identityHashCode(signal)}"
            
          // Get initial value and set up reactive updates
          resource.flatMap { _ =>
            // Combine the initial value setting and the background process into one resource
            Resource.eval(signal.get.flatMap { initialValue =>
              IO {
                idMap.get(spanId).foreach { span =>
                  span.textContent = initialValue.toString
                }
              }
            }).flatMap { _ =>
              // Create a permanent resource for monitoring changes
              Resource.make(
                IO.pure(()) // Acquisition - nothing to do
              )(_ => 
                // Release - cancel the subscription
                signal.discrete
                  .map(_.toString)
                  .changes
                  .evalMap { textValue =>
                    IO {
                      idMap.get(spanId).foreach { span =>
                        span.textContent = textValue
                      }
                    }
                  }
                  .compile
                  .drain
                  .start
                  .flatMap(_.cancel)
              )
            }
          }
      }
    }
  }
}
