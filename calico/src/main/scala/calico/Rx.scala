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

import cats.Monad
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Unique
import cats.syntax.all.*

trait Rx[F[_], +A]:
  // def sources: Set[Unique.Token]

  // def foreach(f: A => F[Unit]): Resource[F, Unit]

  def map[B](f: A => B): Resource[F, Rx[F, B]]

  def map2[B, C](that: Rx[F, B])(f: (A, B) => C): Resource[F, Rx[F, C]]

trait RxSink[F[_], A]:
  def set(a: A): F[Unit]

private abstract class AbstractRxRef[F[_], A](
    val sources: Set[Unique.Token],
    listeners: Ref[F, Set[A => F[Unit]]]
)(using F: Monad[F])
    extends Rx[F, A]
    with RxSink[F, A]:

  def get: F[A] = ???

  def set(a: A): F[Unit] = ???

  def foreach(f: A => F[Unit]): Resource[F, Unit] =
    Resource.make(listeners.update(_ + f) *> (get >>= f))(_ => listeners.update(_ - f))

  def map[B](f: A => B): Resource[F, Rx[F, B]] = ???

  def map2[B, C](that: Rx[F, B])(f: (A, B) => C): Resource[F, Rx[F, C]] = ???

object Rx
