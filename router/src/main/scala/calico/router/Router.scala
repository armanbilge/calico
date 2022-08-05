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

package calico.router

import calico.std.History
import cats.effect.kernel.Async
import cats.effect.kernel.RefSink
import cats.effect.kernel.Resource
import cats.effect.kernel.Unique
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import org.http4s.Uri
import org.scalajs.dom

trait Router[F[_]]:
  def forward: F[Unit]
  def back: F[Unit]
  def go(delta: Int): F[Unit]
  def push(uri: Uri): F[Unit]
  def replace(uri: Uri): F[Unit]
  def location: Signal[F, Uri]

object Router:
  def apply[F[_]](history: History[F, Unit])(f: Router[F] => Resource[F, Routes[F]])(
      using F: Async[F]): Resource[F, dom.HTMLElement] =

    val router = new Router[F]:
      export history.{back, forward, go}
      def push(uri: Uri) = history.pushState((), new dom.URL(uri.renderString))
      def replace(uri: Uri) = history.replaceState((), new dom.URL(uri.renderString))
      def location = new:
        def get = F.delay(dom.window.location.href).flatMap(Uri.fromString(_).liftTo[F])
        def continuous = Stream.repeatEval(get)
        def discrete = history.state.discrete.evalMap(_ => get)

    for
      container <- F.delay {
        dom.document.createElement("div").asInstanceOf[dom.HTMLDivElement]
      }.toResource
      routes <- f(router)
      currentRoute <- Resource.make(
        F.ref(Option.empty[(Unique.Token, RefSink[F, Uri], F[Unit])]))(
        _.get.flatMap(_.fold(F.unit)(_._3)))
      _ <- router
        .location
        .discrete
        .foreach { uri =>
          (currentRoute.get, routes(uri)).flatMapN {
            case (None, None) => F.unit
            case (Some((_, _, finalizer)), None) =>
              F.uncancelable { _ =>
                F.delay(container.replaceChildren()) *> finalizer *> currentRoute.set(None)
              }
            case (None, Some(route)) =>
              F.uncancelable { poll =>
                poll(route.build(uri).allocated).flatMap {
                  case ((sink, child), finalizer) =>
                    F.delay(container.replaceChildren(child)) *>
                      currentRoute.set(Some((route.key, sink, finalizer)))
                }
              }
            case (Some((key, sink, oldFinalizer)), Some(route)) =>
              if route.key === key then sink.set(uri)
              else
                F.uncancelable { poll =>
                  poll(route.build(uri).allocated).flatMap {
                    case ((sink, child), newFinalizer) =>
                      F.delay(container.replaceChildren(child)) *>
                        currentRoute.set(Some((route.key, sink, newFinalizer)))
                  } *> oldFinalizer
                }
          }
        }
        .compile
        .drain
        .background
    yield container