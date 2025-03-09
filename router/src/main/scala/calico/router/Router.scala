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

import cats.effect.kernel.Concurrent
import cats.effect.kernel.RefSink
import cats.effect.kernel.Resource
import cats.effect.kernel.Unique
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.Topic
import fs2.dom.Dom
import fs2.dom.Window
import org.http4s.Uri

/**
 * The `Router` class provides navigation and routing capabilities for a web application.
 * It allows moving forward and backward in the session history, navigating to specific URIs,
 * and dispatching routes to renderable HTML elements.
 *
 * @tparam F The effect type, typically `IO` or another effect type from `cats-effect`.
 */
abstract class Router[F[_]] private ():

  /**
   * Move forward in the session history.
   *
   * @return An effect that completes when the operation is done.
   */
  def forward: F[Unit]

  /**
   * Move backward in the session history.
   *
   * @return An effect that completes when the operation is done.
   */
  def back: F[Unit]

  /**
   * Move forward (positive) or backward (negative) `delta` entries in the session history.
   *
   * @param delta The number of entries to move in the session history.
   * @return An effect that completes when the operation is done.
   */
  def go(delta: Int): F[Unit]

  /**
   * Set the location to a [[Uri]] and add an entry to the history.
   *
   * @param uri The URI to navigate to.
   * @return An effect that completes when the operation is done.
   */
  def navigate(uri: Uri): F[Unit]

  /**
   * Set the location to a [[Uri]] and replace the current history entry.
   *
   * @param uri The URI to teleport to.
   * @return An effect that completes when the operation is done.
   */
  def teleport(uri: Uri): F[Unit]

  /**
   * Get the current location as a signal.
   *
   * @return A signal representing the current location.
   */
  def location: Signal[F, Uri]

  /**
   * Get the number of entries in the history as a signal.
   *
   * @return A signal representing the number of entries in the history.
   */
  def length: Signal[F, Int]

  /**
   * Compile [[Routes]] into a renderable [[fs2.dom.HtmlElement]].
   *
   * @param routes The routes to compile.
   * @return A resource that yields the compiled HTML element.
   */
  def dispatch(routes: Routes[F]): Resource[F, fs2.dom.HtmlElement[F]]

  /**
   * Compile [[Routes]] into a renderable [[fs2.dom.HtmlElement]].
   *
   * @param routes The routes to compile.
   * @return A resource that yields the compiled HTML element.
   */
  def dispatch(routes: F[Routes[F]]): Resource[F, fs2.dom.HtmlElement[F]] =
    Resource.eval(routes).flatMap(dispatch)

object Router:
  /**
   * Create a new `Router` instance.
   *
   * @param window The window object representing the browser window.
   * @param F The concurrent effect type.
   * @return An effect that yields a new `Router` instance.
   */
  def apply[F[_]: Dom](window: Window[F])(using F: Concurrent[F]): F[Router[F]] =
    Topic[F, Uri].map { gps =>
      val history = window.history[Unit]
      new:
        export history.{back, forward, go, length}

        def navigate(uri: Uri) = for
          absUri <- mkAbsolute(uri)
          _ <- history.pushState((), absUri.renderString)
          _ <- gps.publish1(absUri)
        yield ()

        def teleport(uri: Uri) = for
          absUri <- mkAbsolute(uri)
          _ <- history.replaceState((), absUri.renderString)
          _ <- gps.publish1(absUri)
        yield ()

        private def mkAbsolute(uri: Uri): F[Uri] =
          window.location.href.get.flatMap(Uri.fromString(_).liftTo).map(_.resolve(uri))

        def location = new:
          def get = window.location.href.get.flatMap(Uri.fromString(_).liftTo[F])
          def continuous = Stream.repeatEval(get)
          def discrete = history.state.discrete.evalMap(_ => get).merge(gps.subscribe(0))

        def dispatch(routes: Routes[F]) = for
          container <- window.document.createElement("div").toResource
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
                      F.cede *>
                      finalizer
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
                      } *> F.cede *> oldFinalizer
                    }
              }
            }
            .compile
            .drain
            .background
        yield container.asInstanceOf[fs2.dom.HtmlElement[F]]
    }
