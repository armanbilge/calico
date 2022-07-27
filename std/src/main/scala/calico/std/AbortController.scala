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

import cats.effect.kernel.Sync
import cats.effect.kernel.syntax.all.*
import cats.syntax.all.*
import org.scalajs.dom

trait AbortController[F[_]]:
  def control[A](f: dom.AbortSignal => F[A]): F[A]

object AbortController:
  def control[F[_], A](using controller: AbortController[F])(f: dom.AbortSignal => F[A]): F[A] =
    controller.control(f)

  implicit def forSync[F[_]](using F: Sync[F]): AbortController[F] = new:
    def control[A](f: dom.AbortSignal => F[A]): F[A] =
      F.delay(new dom.AbortController).flatMap(c => f(c.signal).onCancel(F.delay(c.abort())))
