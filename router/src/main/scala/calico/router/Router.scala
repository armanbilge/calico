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

import cats.effect.kernel.Async
import cats.effect.kernel.RefSink
import cats.effect.kernel.Resource
import cats.effect.kernel.Unique
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.Topic
import fs2.dom.Dom
import fs2.dom.History
import fs2.dom.Location
import org.http4s.Uri
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor

abstract class Router[F[_]] private ():

  /**
   * move forward in the session history
   */
  def forward: F[Unit]

  /**
   * move backward in the session history
   */
  def back: F[Unit]

  /**
   * move forward (positive) or backward (negative) `delta` entries in the session history
   */
  def go(delta: Int): F[Unit]

  /**
   * set the location to a [[Uri]] and add an entry to the history
   */
  def navigate(uri: Uri): F[Unit]

  /**
   * set the location a [[Uri]] and replace the current history entry
   */
  def teleport(uri: Uri): F[Unit]

  /**
   * the current location
   */
  def location: Signal[F, Uri]

  /**
   * the number of entries in the history
   */
  def length: Signal[F, Int]

  /**
   * Compile [[Routes]] into a renderable [[fs2.dom.HtmlElement]]
   */
  def dispatch(routes: Routes[F]): Resource[F, fs2.dom.HtmlElement[F]]

  /**
   * Compile [[Routes]] into a renderable [[fs2.dom.HtmlElement]]
   */
  def dispatch(routes: F[Routes[F]]): Resource[F, fs2.dom.HtmlElement[F]] =
    Resource.eval(routes).flatMap(dispatch)

object Router:
  def apply[F[_]: Dom](location: Location[F], history: History[F, Unit])(
      using F: Async[F]): F[Router[F]] =
    Topic[F, Uri].map { gps =>
      val _location = location
      new:
        export history.{back, forward, go, length}

        def navigate(uri: Uri) = for
          absUri <- mkAbsolute(uri)
          _ <- history.pushState((), absUri.renderString)
          _ <- gps.publish1(absUri)
        yield ()

        def teleport(uri: Uri) = for
          absUri <- mkAbsolute(uri)
          _ <- history.pushState((), absUri.renderString)
          _ <- gps.publish1(absUri)
        yield ()

        private def mkAbsolute(uri: Uri): F[Uri] =
          _location.href.get.flatMap(Uri.fromString(_).liftTo).map(_.resolve(uri))

        def location = new:
          def get = _location.href.get.flatMap(Uri.fromString(_).liftTo[F])
          def continuous = Stream.repeatEval(get)
          def discrete = history.state.discrete.evalMap(_ => get).merge(gps.subscribe(0))

        def dispatch(routes: Routes[F]) = for
          container <- fs2.dom.Document[F].createElement("div").toResource
          currentRoute <- Resource.make(
            F.ref(Option.empty[(Unique.Token, fs2.dom.Node[F], RefSink[F, Uri], F[Unit])]))(
            _.get.flatMap(_.foldMapA(_._4)))
          _ <- location
            .discrete
            .foreach { uri =>
              (currentRoute.get, routes(uri)).flatMapN {
                case (None, None) => F.unit
                case (Some((_, oldChild, _, finalizer)), None) =>
                  F.uncancelable { _ =>
                    container.removeChild(oldChild) *>
                      currentRoute.set(None) *>
                      finalizer.evalOn(MacrotaskExecutor)
                  }
                case (None, Some(route)) =>
                  F.uncancelable { poll =>
                    poll(route.build(uri).allocated).flatMap {
                      case ((sink, child), finalizer) =>
                        container.appendChild(child) *>
                          currentRoute.set(Some((route.key, child, sink, finalizer)))
                    }
                  }
                case (Some((key, oldChild, sink, oldFinalizer)), Some(route)) =>
                  if route.key === key then sink.set(uri)
                  else
                    F.uncancelable { poll =>
                      poll(route.build(uri).allocated).flatMap {
                        case ((sink, child), newFinalizer) =>
                          container.replaceChild(child, oldChild) *>
                            currentRoute.set(Some((route.key, child, sink, newFinalizer)))
                      } *> oldFinalizer.evalOn(MacrotaskExecutor)
                    }
              }
            }
            .compile
            .drain
            .background
        yield container.asInstanceOf[fs2.dom.HtmlElement[F]]
    }
