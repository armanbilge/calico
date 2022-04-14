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

package calico
package widget

import calico.dsl.Dsl
import calico.syntax.*
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.INothing
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Signal
import org.scalajs.dom
import shapeless3.deriving.K0
import shapeless3.deriving.Labelling

import scala.deriving.Mirror

object Widget:
  def view[F[_], A](sigRef: SigRef[F, A])(using View[F, A]): Resource[F, dom.HTMLElement] =
    view(sigRef.discrete)

  def view[F[_], A](read: Stream[F, A])(using view: View[F, A]): Resource[F, dom.HTMLElement] =
    view.of(read)

  def edit[F[_], A](sigRef: SigRef[F, A])(using Edit[F, A]): Resource[F, dom.HTMLElement] =
    edit(sigRef.discrete)(_.foreach(sigRef.set(_)))

  def edit[F[_], A](read: Stream[F, A])(write: Pipe[F, A, INothing])(
      using edit: Edit[F, A]): Resource[F, dom.HTMLElement] = edit.of(read)(write)

trait View[F[_], A]:
  outer =>

  def of(read: Stream[F, A]): Resource[F, dom.HTMLElement]

  final def contramap[B](f: B => A): View[F, B] = new:
    def of(read: Stream[F, B]) = outer.of(read.map(f))

object View:
  given string[F[_]: Async]: View[F, String] with
    def of(read: Stream[F, String]) = Dsl[F].span(read)

  given int[F[_]: Async]: View[F, Int] = string.contramap(_.toString)

  given product[F[_]: Async, A <: Product](
      using inst: K0.ProductInstances[View[F, _], A],
      labelling: Labelling[A]
  ): View[F, A] with
    def of(read: Stream[F, A]) =
      val dsl = Dsl[F]
      import dsl.*

      read.signal.flatMap { sig =>
        val children = inst
          .unfold(List.empty[Resource[F, dom.HTMLElement]]) {
            [a] =>
              (acc: List[Resource[F, dom.HTMLElement]], view: View[F, a]) =>
                val i = acc.size
                val e = div(
                  b(labelling.elemLabels(i), ": "),
                  view.of(sig.discrete.map(_.productElement(i).asInstanceOf[a]))
                )
                (acc ::: e :: Nil, Some(null.asInstanceOf[a]))
          }
          ._1

        div(children)
      }

trait Edit[F[_], A]:
  def of(read: Stream[F, A])(write: Pipe[F, A, INothing]): Resource[F, dom.HTMLElement]

object Edit:
  given string[F[_]: Async]: Edit[F, String] with
    def of(read: Stream[F, String])(write: Pipe[F, String, INothing]) =
      val dsl = Dsl[F]
      import dsl.*

      input(value <-- read, onInput --> (_.mapToTargetValue.through(write)))

  given int[F[_]: Async]: Edit[F, Int] with
    def of(read: Stream[F, Int])(write: Pipe[F, Int, INothing]) =
      val dsl = Dsl[F]
      import dsl.*

      input(
        typ := "number",
        value <-- read.map(_.toString),
        onInput --> (_.mapToTargetValue.map(_.toIntOption).unNone.through(write))
      )

  given product[F[_]: Async, A <: Product](
      using inst: K0.ProductInstances[Edit[F, _], A],
      mirror: Mirror.ProductOf[A],
      labelling: Labelling[A]
  ): Edit[F, A] with
    def of(read: Stream[F, A])(write: Pipe[F, A, INothing]) =
      val dsl = Dsl[F]
      import dsl.*

      (read.signal, write.channel).tupled.flatMap { (sig, ch) =>
        val children = inst
          .unfold(List.empty[Resource[F, dom.HTMLElement]]) {
            [a] =>
              (acc: List[Resource[F, dom.HTMLElement]], edit: Edit[F, a]) =>
                val i = acc.size
                val e = div(
                  label(
                    b(labelling.elemLabels(i), ": "),
                    edit.of(sig.discrete.map(_.productElement(i).asInstanceOf[a]))(
                      _.foreach { a =>
                        for
                          oldA <- sig.get
                          newA = mirror.fromProduct(updatedProduct(oldA, i, a))
                          _ <- ch.send(newA)
                        yield ()
                      }
                    )
                  )
                )
                (acc ::: e :: Nil, Some(null.asInstanceOf[a]))
          }
          ._1

        div(children)
      }

    def updatedProduct(product: Product, i: Int, a: Any): Product =
      new:
        def canEqual(that: Any) = product.canEqual(that)
        def productArity = product.productArity
        def productElement(n: Int): Any =
          if n == i then a else product.productElement(n)
