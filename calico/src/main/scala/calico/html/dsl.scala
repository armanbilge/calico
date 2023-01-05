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

import calico.html.codecs.Codec
import calico.html.defs.attrs.AriaAttrs
import calico.html.defs.attrs.HtmlAttrs
import calico.html.defs.eventProps.DocumentEventProps
import calico.html.defs.eventProps.GlobalEventProps
import calico.html.defs.eventProps.WindowEventProps
import calico.html.defs.props.HtmlProps
import calico.html.defs.tags.HtmlTags
import calico.html.keys.ClassProp
import calico.html.keys.ClassPropModifiers
import calico.html.keys.EventProp
import calico.html.keys.EventPropModifiers
import calico.html.keys.HtmlAttr
import calico.html.keys.HtmlAttrModifiers
import calico.html.keys.HtmlProp
import calico.html.keys.HtmlProp.apply
import calico.html.keys.HtmlPropModifiers
import calico.html.keys.StyleProp
import calico.html.keys.StylePropModifiers
import calico.html.Modifier
import calico.html.tags.HtmlTag
import calico.syntax.*
import calico.util.DomHotswap
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.Foldable
import cats.Hash
import cats.Monad
import cats.syntax.all.*
import fs2.concurrent.Channel
import fs2.concurrent.Signal
import fs2.Pipe
import fs2.Stream
import org.scalajs.dom
import org.scalajs.dom.Attr
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import shapeless3.deriving.K0

object io extends Html[IO]:
  override val aria: Aria[IO] = Aria[IO]

object Html:
  def apply[F[_]: Async]: Html[F] = new:
    override val aria: Aria[F] = Aria[F]

trait Html[F[_]](using F: Async[F])
    extends HtmlTags[F, HtmlTagT[F]],
      HtmlProps[F],
      GlobalEventProps[F],
      DocumentEventProps[F],
      WindowEventProps[F],
      HtmlAttrs[F],
      HtmlPropModifiers[F],
      EventPropModifiers[F],
      StylePropModifiers[F],
      ClassPropModifiers[F],
      Modifiers[F],
      ChildrenModifiers[F],
      KeyedChildrenModifiers[F],
      HtmlAttrModifiers[F]:
  def aria: Aria[F]

  protected def htmlTag[E <: fs2.dom.HtmlElement[F]](tagName: String, void: Boolean) =
    HtmlTag(tagName, void)

  protected def reflectedAttr[V, J](
      attrKey: String,
      propKey: String,
      attrCodec: Codec[V, String],
      propCodec: Codec[V, J]) =
    HtmlProp(propKey, propCodec)

  protected def prop[V, J](name: String, codec: Codec[V, J]) =
    HtmlProp(name, codec)

  def cls: ClassProp[F] = ClassProp[F]

  def role: HtmlAttr[F, List[String]] = HtmlAttr("role", Codec.whitespaceSeparatedStringsCodec)

  def dataAttr(suffix: String): HtmlAttr[F, List[String]] = HtmlAttr("data-" + suffix, Codec.whitespaceSeparatedStringsCodec)

  def children: Children[F] = Children[F]

  def children[K](f: K => Resource[F, fs2.dom.Node[F]]): KeyedChildren[F, K] =
    KeyedChildren[F, K](f)

  def styleAttr: StyleProp[F] = StyleProp[F]

object Aria:
  def apply[F[_]: Async]: Aria[F] = new Aria[F] {}

trait Aria[F[_]] extends AriaAttrs[F]

type HtmlTagT[F[_]] = [E <: fs2.dom.HtmlElement[F]] =>> HtmlTag[F, E]

final class Children[F[_]] private[calico]:
  import Children.*

  inline def <--(
      cs: Signal[F, List[Resource[F, fs2.dom.Node[F]]]]): ResourceListSignalModifier[F] =
    ResourceListSignalModifier(cs)

  inline def <--(
      cs: Signal[F, Resource[F, List[fs2.dom.Node[F]]]]): ListResourceSignalModifier[F] =
    ListResourceSignalModifier(cs)

object Children:
  final class ResourceListSignalModifier[F[_]](
      val children: Signal[F, List[Resource[F, fs2.dom.Node[F]]]])
  final class ListResourceSignalModifier[F[_]](
      val children: Signal[F, Resource[F, List[fs2.dom.Node[F]]]])

trait ChildrenModifiers[F[_]](using F: Async[F]):
  import Children.*

  inline given forListResourceSignalChildren[N <: fs2.dom.Node[F]]
      : Modifier[F, N, ListResourceSignalModifier[F]] =
    _forListResourceSignalChildren.asInstanceOf[Modifier[F, N, ListResourceSignalModifier[F]]]

  private val _forListResourceSignalChildren
      : Modifier[F, dom.Node, ListResourceSignalModifier[F]] = (m, n) =>
    impl(n, m.children.asInstanceOf[Signal[F, Resource[F, List[dom.Node]]]])

  inline given forResourceListSignalChildren[N <: fs2.dom.Node[F]]
      : Modifier[F, N, ResourceListSignalModifier[F]] =
    _forResourceListSignalChildren.asInstanceOf[Modifier[F, N, ResourceListSignalModifier[F]]]

  private val _forResourceListSignalChildren
      : Modifier[F, dom.Node, ResourceListSignalModifier[F]] = (m, n) =>
    impl(
      n,
      m.children.map { children =>
        def go(
            in: List[Resource[F, dom.Node]],
            out: ListBuffer[dom.Node]
        ): Resource[F, List[dom.Node]] =
          if in.isEmpty then Resource.pure(out.toList)
          else
            in.head.flatMap { c =>
              out += c
              go(in.tail, out)
            }

        go(children.asInstanceOf[List[Resource[F, dom.Node]]], new ListBuffer)
      }
    )

  private def impl(n: dom.Node, children: Signal[F, Resource[F, List[dom.Node]]]) =
    for
      tuple <- children.getAndUpdates
      (head, tail) = tuple
      tuple <- DomHotswap(head)
      (hs, generation0) = tuple
      sentinel <- Resource.eval {
        F.delay {
          generation0.foreach(n.appendChild(_))
          n.appendChild(dom.document.createComment(""))
        }
      }
      _ <- tail
        .foreach { children =>
          hs.swap(children) { (prev, next) =>
            F.delay {
              prev.foreach(n.removeChild)
              next.foreach(n.insertBefore(_, sentinel))
            }
          }
        }
        .compile
        .drain
        .cedeBackground
    yield ()

final class KeyedChildren[F[_], K] private[calico] (f: K => Resource[F, fs2.dom.Node[F]]):
  import KeyedChildren.*
  inline def <--(ks: Signal[F, List[K]]): ListSignalModifier[F, K] = ListSignalModifier(f, ks)

object KeyedChildren:
  final class ListSignalModifier[F[_], K](
      val build: K => Resource[F, fs2.dom.Node[F]],
      val keys: Signal[F, List[K]]
  )

trait KeyedChildrenModifiers[F[_]](using F: Async[F]):
  import KeyedChildren.*

  private def traverse_[A, U](it: Iterable[A])(f: A => F[U]): F[Unit] =
    it.foldLeft(F.unit)(_ <* f(_))

  given forListSignalKeyedChildren[N <: fs2.dom.Node[F], K: Hash]
      : Modifier[F, N, ListSignalModifier[F, K]] = (m, _n) =>
    val n = _n.asInstanceOf[dom.Node]
    inline def build(k: K) = m.build(k).asInstanceOf[Resource[F, dom.Node]]
    for
      tuple <- m.keys.getAndUpdates
      (head, tail) = tuple
      active <- Resource.makeFull[F, Ref[F, mutable.Map[K, (dom.Node, F[Unit])]]] { poll =>
        def go(keys: List[K], active: mutable.Map[K, (dom.Node, F[Unit])]): F[Unit] =
          if keys.isEmpty then F.unit
          else
            val k = keys.head
            poll(build(k).allocated).flatMap { v =>
              active += k -> v
              F.delay(n.appendChild(v._1)) *> go(keys.tail, active)
            }

        F.delay(mutable.Map.empty[K, (dom.Node, F[Unit])])
          .flatTap(active => go(head, active).onCancel(traverse_(active.values)(_._2)))
          .flatMap(F.ref(_))
      }(
        _.get.flatMap(ns => traverse_(ns.values)(_._2)).evalOn(unsafe.BatchingMacrotaskExecutor)
      )
      sentinel <- Resource.eval(F.delay(n.appendChild(dom.document.createComment(""))))
      _ <- tail
        .dropWhile(_ === head)
        .changes
        .foreach { keys =>
          F.uncancelable { poll =>
            active.get.flatMap { currentNodes =>
              F.delay {
                val nextNodes = mutable.Map[K, (dom.Node, F[Unit])]()
                val newNodes = new js.Array[K]
                keys.foreach { k =>
                  currentNodes.remove(k) match
                    case Some(v) => nextNodes += (k -> v)
                    case None => newNodes += k
                }

                val releaseOldNodes = traverse_(currentNodes.values)(_._2)

                val acquireNewNodes = traverse_(newNodes) { k =>
                  poll(build(k).allocated).flatMap(x => F.delay(nextNodes += k -> x))
                }

                val renderNextNodes = F.delay {
                  keys.foreach(k => n.insertBefore(nextNodes(k)._1, sentinel))
                  currentNodes.values.foreach((c, _) => n.removeChild(c))
                }

                (active.set(nextNodes) *> acquireNewNodes *> renderNextNodes).guarantee(
                  releaseOldNodes.evalOn(unsafe.BatchingMacrotaskExecutor)
                )
              }.flatten
            }
          }
        }
        .compile
        .drain
        .cedeBackground
    yield ()


