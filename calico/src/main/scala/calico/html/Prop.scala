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

import cats.Contravariant
import cats.Functor
import cats.FunctorFilter
import cats.Id
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Signal
import org.scalajs.dom

import scala.scalajs.js

sealed class Prop[F[_], V, J] private[calico] (name: String, encode: V => J):
  import Prop.*

  @inline def :=(v: V): ConstantModifier[V, J] =
    ConstantModifier(name, encode, v)

  @inline def <--(vs: Signal[F, V]): SignalModifier[F, V, J] =
    SignalModifier(name, encode, vs)

  @inline def <--(vs: Resource[F, Signal[F, V]]): SignalResourceModifier[F, V, J] =
    SignalResourceModifier(name, encode, vs)

  @inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V, J] =
    OptionSignalModifier(name, encode, vs)

  @inline def <--(
      vs: Resource[F, Signal[F, Option[V]]]): OptionSignalResourceModifier[F, V, J] =
    OptionSignalResourceModifier(name, encode, vs)

  @inline def contramap[U](f: U => V): Prop[F, U, J] =
    new Prop(name, f.andThen(encode))

object Prop:
  inline given [F[_], J]: Contravariant[Prop[F, _, J]] =
    _contravariant.asInstanceOf[Contravariant[Prop[F, _, J]]]
  private val _contravariant: Contravariant[Prop[Id, _, Any]] = new:
    def contramap[A, B](fa: Prop[Id, A, Any])(f: B => A): Prop[Id, B, Any] =
      fa.contramap(f)

  final class ConstantModifier[V, J] private[calico] (
      private[calico] val name: String,
      private[calico] val encode: V => J,
      private[calico] val value: V
  )

  final class SignalModifier[F[_], V, J] private[calico] (
      private[calico] val name: String,
      private[calico] val encode: V => J,
      private[calico] val values: Signal[F, V]
  )

  final class SignalResourceModifier[F[_], V, J] private[calico] (
      private[calico] val name: String,
      private[calico] val encode: V => J,
      private[calico] val values: Resource[F, Signal[F, V]]
  )

  final class OptionSignalModifier[F[_], V, J] private[calico] (
      private[calico] val name: String,
      private[calico] val encode: V => J,
      private[calico] val values: Signal[F, Option[V]]
  )

  final class OptionSignalResourceModifier[F[_], V, J] private[calico] (
      private[calico] val name: String,
      private[calico] val encode: V => J,
      private[calico] val values: Resource[F, Signal[F, Option[V]]]
  )

private trait PropModifiers[F[_]](using F: Async[F]):
  import Prop.*

  private inline def setProp[N, V, J](node: N, name: String, encode: V => J) =
    (value: V) =>
      F.delay {
        node.asInstanceOf[js.Dictionary[J]](name) = encode(value)
        ()
      }

  private inline def setPropOption[N, V, J](node: N, name: String, encode: V => J) =
    (value: Option[V]) =>
      F.delay {
        val dict = node.asInstanceOf[js.Dictionary[Any]]
        value.fold(dict -= name)(v => dict(name) = encode(v))
        ()
      }

  inline given forConstantProp[N, V, J]: Modifier[F, N, ConstantModifier[V, J]] =
    _forConstantProp.asInstanceOf[Modifier[F, N, ConstantModifier[V, J]]]

  private val _forConstantProp: Modifier[F, Any, ConstantModifier[Any, Any]] =
    (m, n) => Resource.eval(setProp(n, m.name, m.encode).apply(m.value))

  inline given forSignalProp[N, V, J]: Modifier[F, N, SignalModifier[F, V, J]] =
    _forSignalProp.asInstanceOf[Modifier[F, N, SignalModifier[F, V, J]]]

  private val _forSignalProp =
    Modifier.forSignal[F, Any, SignalModifier[F, Any, Any], Any](_.values) { (m, n) =>
      setProp(n, m.name, m.encode)
    }

  inline given forSignalResourceProp[N, V, J]: Modifier[F, N, SignalResourceModifier[F, V, J]] =
    _forSignalResourceProp.asInstanceOf[Modifier[F, N, SignalResourceModifier[F, V, J]]]

  private val _forSignalResourceProp =
    Modifier.forSignalResource[F, Any, SignalResourceModifier[F, Any, Any], Any](_.values) {
      (m, n) => setProp(n, m.name, m.encode)
    }

  inline given forOptionSignalProp[N, V, J]: Modifier[F, N, OptionSignalModifier[F, V, J]] =
    _forOptionSignalProp.asInstanceOf[Modifier[F, N, OptionSignalModifier[F, V, J]]]

  private val _forOptionSignalProp =
    Modifier.forSignal[F, Any, OptionSignalModifier[F, Any, Any], Option[Any]](_.values) {
      (m, n) => setPropOption(n, m.name, m.encode)
    }

  inline given forOptionSignalResourceProp[N, V, J]
      : Modifier[F, N, OptionSignalResourceModifier[F, V, J]] =
    _forOptionSignalResourceProp
      .asInstanceOf[Modifier[F, N, OptionSignalResourceModifier[F, V, J]]]

  private val _forOptionSignalResourceProp =
    Modifier.forSignalResource[F, Any, OptionSignalResourceModifier[F, Any, Any], Option[Any]](
      _.values) { (m, n) => setPropOption(n, m.name, m.encode) }

final class EventProp[F[_], A] private[calico] (key: String, pipe: Pipe[F, Any, A]):
  import EventProp.*

  @inline def -->(sink: Pipe[F, A, Nothing]): PipeModifier[F] =
    PipeModifier(key, pipe.andThen(sink))

  @inline def **>(fu: F[Unit]): PipeModifier[F] =
    -->(_.foreach(_ => fu))

  @inline private def through[B](pipe: Pipe[F, A, B]): EventProp[F, B] =
    new EventProp(key, this.pipe.andThen(pipe))

object EventProp:
  @inline private[html] def apply[F[_], E](key: String, f: Any => E): EventProp[F, E] =
    new EventProp(key, _.map(f))

  final class PipeModifier[F[_]] private[calico] (
      private[calico] val key: String,
      private[calico] val sink: Pipe[F, Any, Nothing])

  inline given [F[_]]: (Functor[EventProp[F, _]] & FunctorFilter[EventProp[F, _]]) =
    _functor.asInstanceOf[Functor[EventProp[F, _]] & FunctorFilter[EventProp[F, _]]]
  private val _functor: Functor[EventProp[Id, _]] & FunctorFilter[EventProp[Id, _]] =
    new Functor[EventProp[Id, _]] with FunctorFilter[EventProp[Id, _]]:
      def map[A, B](fa: EventProp[Id, A])(f: A => B) = fa.through(_.map(f))
      def functor = this
      def mapFilter[A, B](fa: EventProp[Id, A])(f: A => Option[B]) =
        fa.through(_.mapFilter(f))

private trait EventPropModifiers[F[_]](using F: Async[F]):
  import EventProp.*
  inline given forPipeEventProp[T <: fs2.dom.Node[F]]: Modifier[F, T, PipeModifier[F]] =
    _forPipeEventProp.asInstanceOf[Modifier[F, T, PipeModifier[F]]]
  private val _forPipeEventProp: Modifier[F, dom.EventTarget, PipeModifier[F]] =
    (m, t) => (F.cede *> fs2.dom.events(t, m.key).through(m.sink).compile.drain).background.void

final class ClassProp[F[_]] private[calico]
    extends Prop[F, List[String], String](
      "className",
      encoders.whitespaceSeparatedStrings
    ):
  import ClassProp.*

  inline def :=(cls: String): SingleConstantModifier =
    SingleConstantModifier(cls)

object ClassProp:
  final class SingleConstantModifier(private[calico] val cls: String)

private trait ClassPropModifiers[F[_]](using F: Async[F]):
  import ClassProp.*
  inline given forConstantClassProp[N]: Modifier[F, N, SingleConstantModifier] =
    _forConstantClassProp.asInstanceOf[Modifier[F, N, SingleConstantModifier]]
  private val _forConstantClassProp: Modifier[F, Any, SingleConstantModifier] =
    (m, n) => Resource.eval(F.delay(n.asInstanceOf[js.Dictionary[String]]("className") = m.cls))
