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

package calico.std

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Channel
import fs2.concurrent.Signal
import io.circe.Decoder
import io.circe.Encoder
import io.circe.scalajs.*
import org.scalajs.dom.EventListenerOptions
import org.scalajs.dom.PopStateEvent
import org.scalajs.dom.URL
import org.scalajs.dom.window

import scala.scalajs.js

sealed trait History[F[_], S]:

  def state: Signal[F, S]
  def length: Signal[F, Int]

  def forward: F[Unit]
  def back: F[Unit]
  def go: F[Unit]
  def go(delta: Int): F[Unit]

  def pushState(state: S): F[Unit]
  def pushState(state: S, url: URL): F[Unit]

  def replaceState(state: S): F[Unit]
  def replaceState(state: S, url: URL): F[Unit]

object History:
  def make[F[_], S: Decoder: Encoder](using F: Async[F]): Resource[F, History[F, S]] =
    Dispatcher[F].map { dispatcher =>
      new:
        def state = new:
          def discrete = Stream.eval(Channel.unbounded[F, js.Any]).flatMap { ch =>
            Stream.resource(AbortController[F].control).flatMap { sig =>
              Stream.eval {
                get <* F.delay {
                  window.addEventListener[PopStateEvent](
                    "popstate",
                    e => dispatcher.unsafeRunAndForget(ch.send(e.state)),
                    new EventListenerOptions:
                      signal = sig
                  )
                }
              } ++ ch.stream.evalMap(decodeJs[S](_).liftTo[F])
            }
          }
          def get = F.delay(window.history.state).flatMap(decodeJs[S](_).liftTo[F])
          def continuous = Stream.repeatEval(get)

        def length = new:
          def discrete = Stream.eval(Channel.unbounded[F, Int]).flatMap { ch =>
            Stream.resource(AbortController[F].control).flatMap { sig =>
              Stream.eval {
                get <* F.delay {
                  window.addEventListener[PopStateEvent](
                    "popstate",
                    e =>
                      dispatcher.unsafeRunAndForget(F.delay(window.history.length) >>= ch.send),
                    new EventListenerOptions:
                      signal = sig
                  )
                }
              } ++ ch.stream
            }
          }
          def get = F.delay(window.history.length)
          def continuous = Stream.repeatEval(get)

        def forward = asyncPopState(window.history.forward())
        def back = asyncPopState(window.history.back())
        def go = asyncPopState(window.history.go())
        def go(delta: Int) = asyncPopState(window.history.go(delta))

        def pushState(state: S) = F.delay(window.history.pushState(state.asJsAny, ""))
        def pushState(state: S, url: URL) =
          F.delay(window.history.pushState(state.asJsAny, "", url.toString))

        def replaceState(state: S) = F.delay(window.history.replaceState(state.asJsAny, ""))
        def replaceState(state: S, url: URL) =
          F.delay(window.history.replaceState(state.asJsAny, "", url.toString))

        def asyncPopState(thunk: => Unit): F[Unit] = F.async_[Unit] { cb =>
          window.addEventListener[PopStateEvent](
            "popstate",
            _ => cb(Either.unit),
            new EventListenerOptions:
              once = true
          )
          thunk
        }

    }
