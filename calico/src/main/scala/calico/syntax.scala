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
package syntax

import cats.Functor
import cats.data.State
import cats.effect.kernel.Async
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Fiber
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Outcome
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.kernel.Eq
import cats.syntax.all.*
import fs2.Pipe
import fs2.Pull
import fs2.Stream
import fs2.concurrent.Channel
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import fs2.concurrent.Topic
import fs2.dom.Dom
import monocle.Lens
import org.scalajs.dom

import scala.scalajs.js

extension [F[_]](component: Resource[F, fs2.dom.Node[F]])
  def renderInto(root: fs2.dom.Node[F])(using Functor[F], Dom[F]): Resource[F, Unit] =
    component.flatMap { e => Resource.make(root.appendChild(e))(_ => root.removeChild(e)) }

extension [F[_], A](sigRef: SignallingRef[F, A])
  def zoom[B](lens: Lens[A, B])(using Functor[F]): SignallingRef[F, B] =
    SignallingRef.lens[F, A, B](sigRef)(lens.get(_), a => b => lens.replace(b)(a))
