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

import calico.html.codecs.Codec
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.concurrent.Signal
import org.scalajs.dom

sealed class HtmlAttr[F[_], V] private[calico] (key: String, codec: Codec[V, String]):
  import HtmlAttr.*

  inline def :=(v: V): ConstantModifier[V] =
    ConstantModifier(key, codec, v)

  inline def <--(vs: Signal[F, V]): SignalModifier[F, V] =
    SignalModifier(key, codec, vs)

  inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V] =
    OptionSignalModifier(key, codec, vs)

object HtmlAttr:
  final class ConstantModifier[V](
      private[calico] val key: String,
      private[calico] val codec: Codec[V, String],
      private[calico] val value: V
  )

  final class SignalModifier[F[_], V](
      private[calico] val key: String,
      private[calico] val codec: Codec[V, String],
      private[calico] val values: Signal[F, V]
  )

  final class OptionSignalModifier[F[_], V](
      private[calico] val key: String,
      private[calico] val codec: Codec[V, String],
      private[calico] val values: Signal[F, Option[V]]
  )

private trait HtmlAttrModifiers[F[_]](using F: Async[F]):
  import HtmlAttr.*

  inline given forConstantHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, ConstantModifier[V]] =
    _forConstantHtmlAttr.asInstanceOf[Modifier[F, E, ConstantModifier[V]]]

  private val _forConstantHtmlAttr: Modifier[F, dom.Element, ConstantModifier[Any]] =
    (m, e) => Resource.eval(F.delay(e.setAttribute(m.key, m.codec.encode(m.value))))

  inline given forSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, SignalModifier[F, V]] =
    _forSignalHtmlAttr.asInstanceOf[Modifier[F, E, SignalModifier[F, V]]]

  private val _forSignalHtmlAttr =
    Modifier.forSignal[F, dom.Element, SignalModifier[F, Any], Any](_.values) { (m, e) => v =>
      F.delay(e.setAttribute(m.key, m.codec.encode(v)))
    }

  inline given forOptionSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, OptionSignalModifier[F, V]] =
    _forOptionSignalHtmlAttr.asInstanceOf[Modifier[F, E, OptionSignalModifier[F, V]]]

  private val _forOptionSignalHtmlAttr =
    Modifier.forSignal[F, dom.Element, OptionSignalModifier[F, Any], Option[Any]](_.values) {
      (m, e) => v =>
        F.delay(v.fold(e.removeAttribute(m.key))(v => e.setAttribute(m.key, m.codec.encode(v))))
    }

final class Aria[F[_]] private extends AriaAttrs[F]

private object Aria:
  inline def apply[F[_]]: Aria[F] = instance.asInstanceOf[Aria[F]]
  private val instance: Aria[cats.Id] = new Aria[cats.Id]

final class AriaAttr[F[_], V] private[calico] (suffix: String, codec: Codec[V, String])
    extends HtmlAttr[F, V]("aria-" + suffix, codec)
