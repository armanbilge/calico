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

import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import org.scalajs.dom

trait AbortController[F[_]]:
  def control: Resource[F, dom.AbortSignal]

object AbortController:
  def apply[F[_]](using F: Sync[F]): AbortController[F] = new:
    def control =
      Resource
        .make(F.delay(new dom.AbortController))(ctrl => F.delay(ctrl.abort()))
        .map(_.signal)
