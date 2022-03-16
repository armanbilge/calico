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

import org.scalajs.dom
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.syntax.all.*

extension [F[_]](component: Resource[F, dom.HTMLElement])
  def renderInto(root: dom.Element)(using F: Sync[F]): Resource[F, Unit] =
    component.flatMap { e =>
      Resource.make(F.delay(root.appendChild(e)))(_ => F.delay(root.removeChild(e))).void
    }
