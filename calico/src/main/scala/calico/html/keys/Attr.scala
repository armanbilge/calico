package calico.html.keys

import calico.html.codecs.Codec
import calico.html.Modifier
import calico.syntax.*
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.concurrent.Signal
import org.scalajs.dom

final class HtmlAttr[F[_], V] private[calico] (key: String, codec: Codec[V, String]):
  import HtmlAttr.*

  inline def :=(v: V): ConstantModifier[V] =
    ConstantModifier(key, codec, v)

  inline def <--(vs: Signal[F, V]): SignalModifier[F, V] =
    SignalModifier(key, codec, vs)

  inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V] =
    OptionSignalModifier(key, codec, vs)

object HtmlAttr:
  final class ConstantModifier[V](
      val key: String,
      val codec: Codec[V, String],
      val value: V
  )

  final class SignalModifier[F[_], V](
      val key: String,
      val codec: Codec[V, String],
      val values: Signal[F, V]
  )

  final class OptionSignalModifier[F[_], V](
      val key: String,
      val codec: Codec[V, String],
      val values: Signal[F, Option[V]]
  )

trait HtmlAttrModifiers[F[_]](using F: Async[F]):
  import HtmlAttr.*

  inline given forConstantHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, ConstantModifier[V]] =
    _forConstantHtmlAttr.asInstanceOf[Modifier[F, E, ConstantModifier[V]]]

  private val _forConstantHtmlAttr: Modifier[F, dom.Element, ConstantModifier[Any]] =
    (m, e) => Resource.eval(F.delay(e.setAttribute(m.key, m.codec.encode(m.value))))

  inline given forSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, SignalModifier[F, V]] =
    _forSignalHtmlAttr.asInstanceOf[Modifier[F, E, SignalModifier[F, V]]]

  private val _forSignalHtmlAttr: Modifier[F, dom.Element, SignalModifier[F, Any]] = (m, e) =>
    m.values.getAndUpdates.flatMap { (head, tail) =>
      def set(v: Any) = F.delay(e.setAttribute(m.key, m.codec.encode(v)))
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

  inline given forOptionSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, OptionSignalModifier[F, V]] =
    _forOptionSignalHtmlAttr.asInstanceOf[Modifier[F, E, OptionSignalModifier[F, V]]]

  private val _forOptionSignalHtmlAttr: Modifier[F, dom.Element, OptionSignalModifier[F, Any]] =
    (m, e) =>
      m.values.getAndUpdates.flatMap { (head, tail) =>
        def set(v: Option[Any]) = F.delay {
          v.fold(e.removeAttribute(m.key))(v => e.setAttribute(m.key, m.codec.encode(v)))
        }
        Resource.eval(set(head)) *>
          tail.foreach(set(_)).compile.drain.cedeBackground.void
      }
