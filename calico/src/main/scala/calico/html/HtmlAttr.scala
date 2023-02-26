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

package calico.html

import cats.Contravariant
import cats.Id
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.concurrent.Signal
import org.scalajs.dom

sealed class HtmlAttr[F[_], V] private[calico] (key: String, encode: V => String):
  import HtmlAttr.*

  @inline def :=(v: V): ConstantModifier[V] =
    ConstantModifier(key, encode, v)

  @inline def <--(vs: Signal[F, V]): SignalModifier[F, V] =
    SignalModifier(key, encode, vs)

  @inline def <--(vs: Resource[F, Signal[F, V]]): SignalResourceModifier[F, V] =
    SignalResourceModifier(key, encode, vs)

  @inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V] =
    OptionSignalModifier(key, encode, vs)

  @inline def <--(vs: Resource[F, Signal[F, Option[V]]]): OptionSignalResourceModifier[F, V] =
    OptionSignalResourceModifier(key, encode, vs)

  @inline def contramap[U](f: U => V): HtmlAttr[F, U] =
    new HtmlAttr(key, f.andThen(encode))

object HtmlAttr:
  inline given [F[_]]: Contravariant[HtmlAttr[F, _]] =
    _contravariant.asInstanceOf[Contravariant[HtmlAttr[F, _]]]
  private val _contravariant: Contravariant[HtmlAttr[Id, _]] = new:
    def contramap[A, B](fa: HtmlAttr[Id, A])(f: B => A): HtmlAttr[Id, B] =
      fa.contramap(f)

  final class ConstantModifier[V] private[calico] (
      private[calico] val key: String,
      private[calico] val encode: V => String,
      private[calico] val value: V
  )

  final class SignalModifier[F[_], V] private[calico] (
      private[calico] val key: String,
      private[calico] val encode: V => String,
      private[calico] val values: Signal[F, V]
  )

  final class SignalResourceModifier[F[_], V] private[calico] (
      private[calico] val key: String,
      private[calico] val encode: V => String,
      private[calico] val values: Resource[F, Signal[F, V]]
  )

  final class OptionSignalModifier[F[_], V] private[calico] (
      private[calico] val key: String,
      private[calico] val encode: V => String,
      private[calico] val values: Signal[F, Option[V]]
  )

  final class OptionSignalResourceModifier[F[_], V] private[calico] (
      private[calico] val key: String,
      private[calico] val encode: V => String,
      private[calico] val values: Resource[F, Signal[F, Option[V]]]
  )

private trait HtmlAttrModifiers[F[_]](using F: Async[F]):
  import HtmlAttr.*

  inline given forConstantHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, ConstantModifier[V]] =
    _forConstantHtmlAttr.asInstanceOf[Modifier[F, E, ConstantModifier[V]]]

  private val _forConstantHtmlAttr: Modifier[F, dom.Element, ConstantModifier[Any]] =
    (m, e) => Resource.eval(F.delay(e.setAttribute(m.key, m.encode(m.value))))

  inline given forSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, SignalModifier[F, V]] =
    _forSignalHtmlAttr.asInstanceOf[Modifier[F, E, SignalModifier[F, V]]]

  private val _forSignalHtmlAttr =
    Modifier.forSignal[F, dom.Element, SignalModifier[F, Any], Any](_.values) { (m, e) => v =>
      F.delay(e.setAttribute(m.key, m.encode(v)))
    }

  inline given forSignalResourceHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, SignalResourceModifier[F, V]] =
    _forSignalResourceHtmlAttr.asInstanceOf[Modifier[F, E, SignalResourceModifier[F, V]]]

  private val _forSignalResourceHtmlAttr =
    Modifier.forSignalResource[F, dom.Element, SignalResourceModifier[F, Any], Any](_.values) {
      (m, e) => v => F.delay(e.setAttribute(m.key, m.encode(v)))
    }

  inline given forOptionSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, OptionSignalModifier[F, V]] =
    _forOptionSignalHtmlAttr.asInstanceOf[Modifier[F, E, OptionSignalModifier[F, V]]]

  private val _forOptionSignalHtmlAttr =
    Modifier.forSignal[F, dom.Element, OptionSignalModifier[F, Any], Option[Any]](_.values) {
      (m, e) => v =>
        F.delay(v.fold(e.removeAttribute(m.key))(v => e.setAttribute(m.key, m.encode(v))))
    }

  inline given forOptionSignalResourceHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, OptionSignalResourceModifier[F, V]] =
    _forOptionSignalResourceHtmlAttr.asInstanceOf[Modifier[F, E, OptionSignalResourceModifier[F, V]]]

  private val _forOptionSignalResourceHtmlAttr =
    Modifier
      .forSignalResource[F, dom.Element, OptionSignalResourceModifier[F, Any], Option[Any]](
        _.values) { (m, e) => v =>
        F.delay(v.fold(e.removeAttribute(m.key))(v => e.setAttribute(m.key, m.encode(v))))
      }

final class Aria[F[_]] private extends AriaAttrs[F]

private object Aria:
  inline def apply[F[_]]: Aria[F] = instance.asInstanceOf[Aria[F]]
  private val instance: Aria[cats.Id] = new Aria[cats.Id]

final class AriaAttr[F[_], V] private[calico] (suffix: String, encode: V => String)
    extends HtmlAttr[F, V]("aria-" + suffix, encode)
