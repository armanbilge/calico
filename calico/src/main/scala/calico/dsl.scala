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
package dsl

import calico.syntax.*
import cats.Foldable
import cats.Hash
import cats.Monad
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.std.Dispatcher
import cats.effect.std.Hotswap
import cats.effect.std.Supervisor
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.raquo.domtypes.generic.builders.EventPropBuilder
import com.raquo.domtypes.generic.builders.HtmlAttrBuilder
import com.raquo.domtypes.generic.builders.HtmlTagBuilder
import com.raquo.domtypes.generic.builders.PropBuilder
import com.raquo.domtypes.generic.builders.ReflectedHtmlAttrBuilder
import com.raquo.domtypes.generic.codecs.Codec
import com.raquo.domtypes.generic.defs.attrs.*
import com.raquo.domtypes.generic.defs.complex.*
import com.raquo.domtypes.generic.defs.props.*
import com.raquo.domtypes.generic.defs.reflectedAttrs.*
import com.raquo.domtypes.jsdom.defs.eventProps.*
import com.raquo.domtypes.jsdom.defs.tags.*
import fs2.INothing
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Channel
import org.scalajs.dom
import shapeless3.deriving.K0

import scala.collection.mutable
import scala.scalajs.js

object io extends Dsl[IO]

object Dsl:
  def apply[F[_]: Async]: Dsl[F] = new Dsl[F] {}

trait Dsl[F[_]]
    extends HtmlBuilders[F],
      DocumentTags[HtmlTagT[F]],
      GroupingTags[HtmlTagT[F]],
      TextTags[HtmlTagT[F]],
      FormTags[HtmlTagT[F]],
      SectionTags[HtmlTagT[F]],
      EmbedTags[HtmlTagT[F]],
      TableTags[HtmlTagT[F]],
      MiscTags[HtmlTagT[F]],
      HtmlAttrs[HtmlAttr[F, _]],
      ReflectedHtmlAttrs[Prop[F, _, _]],
      Props[Prop[F, _, _]],
      ClipboardEventProps[EventProp[F, _]],
      ErrorEventProps[EventProp[F, _]],
      FormEventProps[EventProp[F, _]],
      KeyboardEventProps[EventProp[F, _]],
      MediaEventProps[EventProp[F, _]],
      MiscellaneousEventProps[EventProp[F, _]],
      MouseEventProps[EventProp[F, _]],
      PointerEventProps[EventProp[F, _]]

trait HtmlBuilders[F[_]](using F: Async[F])
    extends HtmlTagBuilder[HtmlTagT[F], dom.HTMLElement],
      HtmlAttrBuilder[HtmlAttr[F, _]],
      ReflectedHtmlAttrBuilder[Prop[F, _, _]],
      PropBuilder[Prop[F, _, _]],
      EventPropBuilder[EventProp[F, _], dom.Event]:

  protected def htmlTag[E <: dom.HTMLElement](tagName: String, void: Boolean) =
    HtmlTag(tagName, void)

  protected def htmlAttr[V](key: String, codec: Codec[V, String]) =
    HtmlAttr(key, codec)

  protected def reflectedAttr[V, J](
      attrKey: String,
      propKey: String,
      attrCodec: Codec[V, String],
      propCodec: Codec[V, J]) =
    Prop(propKey, propCodec)

  protected def prop[V, J](name: String, codec: Codec[V, J]) =
    Prop(name, codec)

  def eventProp[V <: dom.Event](key: String): EventProp[F, V] =
    EventProp(key)

  def cls: ClassAttr[F] = ClassAttr[F]

  def children: Children[F] = Children[F]

  def children[K, E <: dom.Element](f: K => Resource[F, E]): KeyedChildren[F, K, E] =
    KeyedChildren[F, K, E](f)

type HtmlTagT[F[_]] = [E <: dom.HTMLElement] =>> HtmlTag[F, E]
final class HtmlTag[F[_], E <: dom.HTMLElement] private[calico] (name: String, void: Boolean)(
    using F: Async[F]):

  def apply[M](modifier: M)(using Modifier[F, E, M]): Resource[F, E] =
    apply(Tuple1(modifier))

  def apply[M](mkModifier: E => M)(using Modifier[F, E, M]): Resource[F, E] =
    apply(e => Tuple1(mkModifier(e)))

  def apply[M <: Tuple](modifiers: M)(
      using K0.ProductInstances[Modifier[F, E, _], M]): Resource[F, E] =
    apply(_ => modifiers)

  def apply[M <: Tuple](mkModifiers: E => M)(
      using inst: K0.ProductInstances[Modifier[F, E, _], M]): Resource[F, E] =
    build.toResource.flatMap { e =>
      inst.foldLeft(mkModifiers(e))(Resource.pure(e)) {
        [a] => (r: Resource[F, E], m: Modifier[F, E, a], a: a) => r.flatTap(m.modify(a, _))
      }
    }

  private def build = F.delay(dom.document.createElement(name).asInstanceOf[E])

trait Modifier[F[_], E, A]:
  outer =>

  def modify(a: A, e: E): Resource[F, Unit]

  final def contramap[B](f: B => A): Modifier[F, E, B] =
    (b: B, e: E) => outer.modify(f(b), e)

object Modifier:
  given forUnit[F[_], E]: Modifier[F, E, Unit] with
    def modify(unit: Unit, e: E) = Resource.unit

  given forString[F[_], E <: dom.Node](using F: Async[F]): Modifier[F, E, String] =
    forStringStream.contramap(Stream.emit(_))

  given forStringStream[F[_], E <: dom.Node](
      using F: Async[F]): Modifier[F, E, Stream[F, String]] with
    def modify(s: Stream[F, String], e: E) = for
      n <- F
        .delay(dom.document.createTextNode(""))
        .flatTap(n => F.delay(e.appendChild(n)))
        .toResource
      _ <- s.foreach(t => F.delay(n.textContent = t)).compile.drain.background
    yield ()

  given forResource[F[_], E <: dom.Node, A](
      using M: Modifier[F, E, A]): Modifier[F, E, Resource[F, A]] with
    def modify(a: Resource[F, A], e: E) = a.flatMap(M.modify(_, e))

  given forFoldable[F[_]: Monad, E <: dom.Node, G[_]: Foldable, A](
      using M: Modifier[F, E, A]): Modifier[F, E, G[A]] with
    def modify(ga: G[A], e: E) = ga.foldMapM(M.modify(_, e)).void

  given forElement[F[_], E <: dom.Node, E2 <: dom.Node](
      using F: Sync[F]): Modifier[F, E, Resource[F, E2]] with
    def modify(e2: Resource[F, E2], e: E) =
      e2.evalMap(e2 => F.delay(e.appendChild(e2)))

  given forElementStream[F[_], E <: dom.Node, E2 <: dom.Node](
      using F: Async[F]): Modifier[F, E, Stream[F, Resource[F, E2]]] with
    def modify(e2s: Stream[F, Resource[F, E2]], e: E) =
      for
        (hs, c) <- Hotswap[F, dom.Node](Resource.eval(F.delay(dom.document.createComment(""))))
        prev <- F.ref(c).toResource
        _ <- e2s
          .evalMap { next =>
            for
              n <- hs.swap(next.widen)
              p <- prev.get
              _ <- F.delay(e.replaceChild(p, n))
              _ <- prev.set(n)
            yield ()
          }
          .compile
          .drain
          .background
      yield ()

final class HtmlAttr[F[_], V] private[calico] (key: String, codec: Codec[V, String]):
  def :=(v: V): HtmlAttr.Modified[F, V] =
    this <-- Stream.emit(v)

  def <--(vs: Stream[F, V]): HtmlAttr.Modified[F, V] =
    this <-- Resource.pure(vs)

  def <--(vs: Resource[F, Stream[F, V]]): HtmlAttr.Modified[F, V] =
    HtmlAttr.Modified(key, codec, vs)

object HtmlAttr:
  final class Modified[F[_], V] private[HtmlAttr] (
      val key: String,
      val codec: Codec[V, String],
      val values: Resource[F, Stream[F, V]]
  )

  given [F[_], E <: dom.Element, V](using F: Async[F]): Modifier[F, E, Modified[F, V]] with
    def modify(attr: Modified[F, V], e: E) =
      attr.values.flatMap { vs =>
        vs.foreach(v => F.delay(e.setAttribute(attr.key, attr.codec.encode(v))))
          .compile
          .drain
          .background
          .void
      }

sealed class Prop[F[_], V, J] private[calico] (name: String, codec: Codec[V, J]):
  def :=(v: V): Prop.Modified[F, V, J] =
    this <-- Stream.emit(v)

  def <--(vs: Stream[F, V]): Prop.Modified[F, V, J] =
    this <-- Resource.pure(vs)

  def <--(vs: Resource[F, Stream[F, V]]): Prop.Modified[F, V, J] =
    Prop.Modified(name, codec, vs)

object Prop:
  final class Modified[F[_], V, J] private[Prop] (
      val name: String,
      val codec: Codec[V, J],
      val values: Resource[F, Stream[F, V]]
  )

  given [F[_], E, V, J](using F: Async[F]): Modifier[F, E, Modified[F, V, J]] with
    def modify(prop: Modified[F, V, J], e: E) =
      prop.values.flatMap { vs =>
        vs.foreach { v =>
          F.delay(e.asInstanceOf[js.Dictionary[J]](prop.name) = prop.codec.encode(v))
        }.compile
          .drain
          .background
          .void
      }

final class EventProp[F[_], E] private[calico] (key: String):
  def -->(sink: Pipe[F, E, INothing]): EventProp.Modified[F, E] = EventProp.Modified(key, sink)

object EventProp:
  final class Modified[F[_], E] private[calico] (
      val key: String,
      val sink: Pipe[F, E, INothing])

  given [F[_], E <: dom.EventTarget, V](using F: Async[F]): Modifier[F, E, Modified[F, V]] with
    def modify(prop: Modified[F, V], e: E) = for
      ch <- Resource.make(Channel.unbounded[F, V])(_.close.void)
      d <- Dispatcher[F]
      _ <- Resource.make {
        F.delay(new dom.AbortController).flatTap { c =>
          F.delay {
            e.addEventListener(
              prop.key,
              e => d.unsafeRunAndForget(ch.send(e.asInstanceOf[V])),
              js.Dynamic.literal(signal = c.signal).asInstanceOf[dom.EventListenerOptions]
            )
          }
        }
      } { c => F.delay(c.abort()) }
      _ <- ch.stream.through(prop.sink).compile.drain.background
    yield ()

final class ClassAttr[F[_]] private[calico]
    extends Prop[F, List[String], String](
      "className",
      new:
        def decode(domValue: String) = domValue.split(" ").toList
        def encode(scalaValue: List[String]) = scalaValue.mkString(" ")
    ):

  def :=(cls: String): Prop.Modified[F, List[String], String] =
    this := List(cls)

final class Children[F[_]] private[calico]:
  def <--(cs: Stream[F, List[Resource[F, dom.Node]]])(using Monad[F]): Children.Modified[F] =
    Children.Modified(cs.map(_.sequence))

object Children:
  final class Modified[F[_]] private[calico] (val cs: Stream[F, Resource[F, List[dom.Node]]])

  given [F[_], E <: dom.Element](using F: Async[F]): Modifier[F, E, Modified[F]] with
    def modify(children: Modified[F], e: E) =
      Stream
        .resource(Hotswap.create[F, Unit])
        .flatMap { hs =>
          children.cs.evalMap(c => hs.swap(c.evalMap(c => F.delay(e.replaceChildren(c*)))))
        }
        .compile
        .drain
        .background
        .void

final class KeyedChildren[F[_], K, E <: dom.Element] private[calico] (f: K => Resource[F, E]):
  def <--(ks: Stream[F, List[K]]): KeyedChildren.Modified[F, K, E] =
    KeyedChildren.Modified(f, ks)

object KeyedChildren:
  final class Modified[F[_], K, E <: dom.Element] private[calico] (
      val f: K => Resource[F, E],
      val ks: Stream[F, List[K]])

  given [F[_], E <: dom.Element, K: Hash, E2 <: dom.Element](
      using F: Async[F]): Modifier[F, E, Modified[F, K, E2]] with
    def modify(children: Modified[F, K, E2], e: E) =
      for
        sup <- Supervisor[F]
        active <- Resource.make(Ref[F].of(mutable.Map.empty[K, (E2, F[Unit])]))(
          _.get.flatMap(_.values.toList.traverse_(_._2))
        )
        _ <- children
          .ks
          .evalMap { ks =>
            active.access.flatMap { (currentNodes, update) =>
              F.uncancelable { poll =>
                F.delay {
                  val nextNodes = mutable.Map[K, (E2, F[Unit])]()
                  val newNodes = List.newBuilder[K]
                  ks.foreach { k =>
                    currentNodes.remove(k) match
                      case Some(v) => nextNodes += (k -> v)
                      case None => newNodes += k
                  }

                  val releaseOldNodes = currentNodes.values.toList.traverse_(_._2)

                  val acquireNewNodes = newNodes.result().traverse_ { k =>
                    poll(children.f(k).allocated).flatMap(x => F.delay(nextNodes += k -> x))
                  }

                  val renderNextNodes = F.delay(e.replaceChildren(ks.map(nextNodes(_)._1)*))

                  (update(nextNodes) *>
                    acquireNewNodes *>
                    renderNextNodes).guarantee(releaseOldNodes)
                }.flatten
              }
            }
          }
          .compile
          .drain
          .background
      yield ()
