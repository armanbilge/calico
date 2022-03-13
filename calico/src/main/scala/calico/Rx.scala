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
import cats.effect.IO
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.kernel.Unique
import cats.syntax.all.*
import cats.effect.syntax.all.*
import cats.~>
import fs2.Pull
import fs2.Stream

trait Reactive[F[_]]:

  def rx[A](a: A): F[Rx[F, A]]

  def stream[A](sa: Stream[F, A]): Resource[F, RxSource[F, A]]

object Reactive:
  given Reactive[IO] with
    def rx[A](a: A): IO[Rx[IO, A]] = ???

    def stream[A](sa: Stream[IO, A]): Resource[IO, RxSource[IO, A]] =
      sa.pull.uncons1.flatMap(Pull.output1).stream.unNone.compile.resource.lastOrError.flatMap {
        (a, tail) =>
          Resource.eval(rx(a)).flatTap { rx => tail.foreach(rx.set).compile.resource.drain }
      }

trait RxSource[F[_], A]:
  def map[B](f: A => B): Resource[F, RxSource[F, B]]
  def foreach(f: A => F[Unit]): Resource[F, Unit]

trait RxSink[F[_], A]:
  def set(a: A): F[Unit]

trait Rx[F[_], A] extends RxSource[F, A], RxSink[F, A]

private final class RxRef[F[_], A](
    value: Ref[F, A],
    listeners: Ref[F, Set[A => F[Unit]]]
)(using F: Sync[F])
    extends Rx[F, A]:

  def set(a: A): F[Unit] = value.set(a)

  def foreach(f: A => F[Unit]): Resource[F, Unit] =
    Resource.make {
      listeners.update(_ + f) *> (value.get >>= f)
    } { _ => listeners.update(_ - f) }

  def map[B](f: A => B): Resource[F, Rx[F, B]] =
    value.get.map(f).flatMap(RxRef(_)).toResource.flatTap { rx => foreach(set) }

private object RxRef:
  def apply[F[_], B](b: B)(using F: Sync[F]): F[RxRef[F, B]] = for
    value <- Ref.of(b)
    listeners <- Ref.of(Set.empty[B => F[Unit]])
    rx <- F.delay(new RxRef(value, listeners))
  yield rx
