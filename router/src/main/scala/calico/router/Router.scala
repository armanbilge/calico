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
import cats.syntax.all.*
import fs2.concurrent.Signal
import org.http4s.Uri.Path
import org.scalajs.dom

trait Router[F[_]]:
  def forward: F[Unit]
  def back: F[Unit]
  def go(delta: Int): F[Unit]
  def push(path: Path): F[Unit]
  def replace(path: Path): F[Unit]
  def location: Signal[F, Path]

object Router:
  def apply[F[_]](history: History[F, Unit])(f: Router[F] => Routes[F])(
      using F: Async[F]): Resource[F, dom.HTMLElement] =

    val router = new Router[F]:
      export history.{back, forward, go}
      def push(path: Path) = history.pushState((), new dom.URL(path.renderString))
      def replace(path: Path) = history.replaceState((), new dom.URL(path.renderString))
      def location = ???

    Resource
      .eval(F.delay(dom.document.createElement("div").asInstanceOf[dom.HTMLDivElement]))
      .flatTap { container =>
        Resource.eval(F.ref(Option.empty[(RefSink[F, Path], F[Unit])])).flatMap { currentNode =>
          ???
        }
      }
