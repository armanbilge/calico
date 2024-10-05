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
package html

import cats.effect.kernel.Concurrent
import cats.effect.kernel.Resource
import cats.syntax.all.*

private abstract class DomHotswap[F[_], A]:
  def swap(next: Resource[F, A])(render: (A, A) => F[Unit]): F[Unit]

private object DomHotswap:
  def apply[F[_], A](init: Resource[F, A])(
      using F: Concurrent[F]
  ): Resource[F, (DomHotswap[F, A], A)] =
    Resource.make(init.allocated.flatMap(F.ref(_)))(_.get.flatMap(_._2)).evalMap { active =>
      val hs = new DomHotswap[F, A]:
        def swap(next: Resource[F, A])(render: (A, A) => F[Unit]) = F.uncancelable { poll =>
          for
            nextAllocated <- poll(next.allocated)
            (oldA, oldFinalizer) <- active.getAndSet(nextAllocated)
            newA = nextAllocated._1
            _ <- render(oldA, newA) *> F.cede *> oldFinalizer
          yield ()
        }

      active.get.map(_._1).tupleLeft(hs)
    }
