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

import calico.html.Modifier
import cats.Functor
import cats.effect.kernel.Resource
import fs2.concurrent.SignallingRef
import fs2.dom.Dom
import monocle.Lens

extension [F[_]](component: Resource[F, fs2.dom.Node[F]])
  def renderInto(root: fs2.dom.Node[F])(using Functor[F], Dom[F]): Resource[F, Unit] =
    component.flatMap { e => Resource.make(root.appendChild(e))(_ => root.removeChild(e)) }

extension [F[_], A](sigRef: SignallingRef[F, A])
  def zoom[B](lens: Lens[A, B])(using Functor[F]): SignallingRef[F, B] =
    SignallingRef.lens[F, A, B](sigRef)(lens.get(_), a => b => lens.replace(b)(a))

extension [E](e: E)
  inline def modify[F[_], A](a: A)(using m: Modifier[F, E, A]): Resource[F, Unit] =
    m.modify(a, e)
