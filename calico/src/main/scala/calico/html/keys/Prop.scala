package calico.html.keys

import calico.html.codecs.Codec
import calico.html.Modifier
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.concurrent.Signal
import fs2.Pipe
import org.scalajs.dom
import scala.scalajs.js

sealed class HtmlProp[F[_], V, J] private[calico] (name: String, codec: Codec[V, J]):
  import HtmlProp.*

  inline def :=(v: V): ConstantModifier[V, J] =
    ConstantModifier(name, codec, v)

  inline def <--(vs: Signal[F, V]): SignalModifier[F, V, J] =
    SignalModifier(name, codec, vs)

  inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V, J] =
    OptionSignalModifier(name, codec, vs)

object HtmlProp:
  final class ConstantModifier[V, J](
      val name: String,
      val codec: Codec[V, J],
      val value: V
  )

  final class SignalModifier[F[_], V, J](
      val name: String,
      val codec: Codec[V, J],
      val values: Signal[F, V]
  )

  final class OptionSignalModifier[F[_], V, J](
      val name: String,
      val codec: Codec[V, J],
      val values: Signal[F, Option[V]]
  )

trait HtmlPropModifiers[F[_]](using F: Async[F]):
  import HtmlProp.*

  private[calico] inline def setHtmlProp[N, V, J](
      node: N,
      value: V,
      name: String,
      codec: Codec[V, J]) =
    F.delay(node.asInstanceOf[js.Dictionary[J]](name) = codec.encode(value))

  inline given forConstantHtmlProp[N, V, J]: Modifier[F, N, ConstantModifier[V, J]] =
    _forConstantHtmlProp.asInstanceOf[Modifier[F, N, ConstantModifier[V, J]]]

  private val _forConstantHtmlProp: Modifier[F, Any, ConstantModifier[Any, Any]] =
    (m, n) => Resource.eval(setHtmlProp(n, m.value, m.name, m.codec))

  inline given forSignalHtmlProp[N, V, J]: Modifier[F, N, SignalModifier[F, V, J]] =
    _forSignalHtmlProp.asInstanceOf[Modifier[F, N, SignalModifier[F, V, J]]]

  private val _forSignalHtmlProp: Modifier[F, Any, SignalModifier[F, Any, Any]] =
    Modifier.forSignal[F, Any, SignalModifier[F, Any, Any], Any]((any, m, v) =>
      setHtmlProp(any, v, m.name, m.codec))(_.values)

  inline given forOptionSignalHtmlProp[N, V, J]: Modifier[F, N, OptionSignalModifier[F, V, J]] =
    _forOptionSignalHtmlProp.asInstanceOf[Modifier[F, N, OptionSignalModifier[F, V, J]]]

  private val _forOptionSignalHtmlProp: Modifier[F, Any, OptionSignalModifier[F, Any, Any]] =
    Modifier.forSignal[F, Any, OptionSignalModifier[F, Any, Any], Option[Any]](
      (any, osm, oany) =>
        F.delay {
          val dict = any.asInstanceOf[js.Dictionary[Any]]
          oany.fold(dict -= osm.name)(v => dict(osm.name) = osm.codec.encode(v))
          ()
        })(_.values)

final class EventProp[F[_], E] private[calico] (key: String):
  import EventProp.*
  inline def -->(sink: Pipe[F, E, Nothing]): PipeModifier[F, E] = PipeModifier(key, sink)

object EventProp:
  final class PipeModifier[F[_], E](val key: String, val sink: Pipe[F, E, Nothing])

trait EventPropModifiers[F[_]](using F: Async[F]):
  import EventProp.*
  inline given forPipeEventProp[T <: fs2.dom.Node[F], E]: Modifier[F, T, PipeModifier[F, E]] =
    _forPipeEventProp.asInstanceOf[Modifier[F, T, PipeModifier[F, E]]]
  private val _forPipeEventProp: Modifier[F, dom.EventTarget, PipeModifier[F, Any]] =
    (m, t) => fs2.dom.events(t, m.key).through(m.sink).compile.drain.cedeBackground.void
