/*
 * Copyright 2024 Calico Contributors
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

import cats.effect.kernel.{Async, Resource}
import fs2.dom.{HtmlInputElement, Node, File}
import fs2.{Pipe, Stream}
import java.time.LocalDate
import scala.util.Try

/** A type-safe input component that provides strongly typed events based on the input type */
sealed trait TypedInput[F[_], A] {
  def render: Resource[F, HtmlInputElement[F]]
  def events: Pipe[F, fs2.dom.Event[F], A]
}

object TypedInput {
  private def getValue[F[_]: Async](e: fs2.dom.Event[F]): F[String] = 
    e.target.asInstanceOf[fs2.dom.HtmlInputElement[F]].value.get

  def apply[F[_]: Async, A](inputType: String, parser: String => Option[A]): TypedInput[F, A] = 
    new TypedInput[F, A] {
      def render: Resource[F, HtmlInputElement[F]] = {
        import calico.html.io.*
        input.withSelf { self =>
          typ := inputType
        }
      }

      def events: Pipe[F, fs2.dom.Event[F], A] = 
        _.evalMap(e => getValue(e).flatMap(value => 
          Async[F].fromOption(
            parser(value),
            new IllegalStateException(s"Failed to parse input value '$value' as ${implicitly[scala.reflect.ClassTag[A]].runtimeClass.getSimpleName}")
          )
        ))
    }

  // Common input type implementations
  def file[F[_]: Async]: TypedInput[F, File[F]] = 
    new TypedInput[F, File[F]] {
      def render: Resource[F, HtmlInputElement[F]] = {
        import calico.html.io.*
        input.withSelf { self =>
          typ := "file"
        }
      }

      def events: Pipe[F, fs2.dom.Event[F], File[F]] = 
        _.evalMap(e => 
          e.target.asInstanceOf[fs2.dom.HtmlInputElement[F]].files.get.flatMap { files =>
            Async[F].fromOption(
              Option(files(0)),
              new IllegalStateException("No file selected")
            )
          }
        )
    }

  def number[F[_]: Async]: TypedInput[F, Double] = 
    apply[F, Double]("number", str => Try(str.toDouble).toOption)

  def text[F[_]: Async]: TypedInput[F, String] = 
    apply[F, String]("text", Some(_))

  def date[F[_]: Async]: TypedInput[F, LocalDate] = 
    apply[F, LocalDate]("date", str => Try(LocalDate.parse(str)).toOption)

  def email[F[_]: Async]: TypedInput[F, String] = 
    apply[F, String]("email", str => 
      if (str.matches(".+@.+\\..+")) Some(str) else None
    )

  def password[F[_]: Async]: TypedInput[F, String] = 
    apply[F, String]("password", Some(_))
} 