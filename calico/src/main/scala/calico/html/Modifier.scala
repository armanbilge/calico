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
import cats.Id
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.concurrent.Signal
import org.scalajs.dom
import shapeless3.deriving.K0

import scala.annotation.nowarn

trait Modifier[F[_], E, A]:
  outer =>

  def modify(a: A, e: E): Resource[F, Unit]

  @nowarn("msg=New anonymous class definition will be duplicated at each inline site")
  inline final def contramap[B](inline f: B => A): Modifier[F, E, B] =
    (b: B, e: E) => outer.modify(f(b), e)

  extension (a: A)
    inline final def toMod[E0 <: E]: Mod[F, E0] =
      Mod(a)(using this.asInstanceOf[Modifier[F, E0, A]])

/**
 * A reified modifier
 */
trait Mod[F[_], E]:
  protected type M
  protected def mod: M
  protected def modifier: Modifier[F, E, M]

object Mod:
  def apply[F[_], E, A](a: A)(using Modifier[F, E, A]): Mod[F, E] =
    new:
      type M = A
      def mod = a
      def modifier = summon[Modifier[F, E, M]]

  given [F[_], E, E0 <: E]: Modifier[F[_], E0, Mod[F, E]] = (m, e) =>
    m.modifier.modify(m.mod, e)

object Modifier:
  inline given forUnit[F[_], E]: Modifier[F, E, Unit] =
    _forUnit.asInstanceOf[Modifier[F, E, Unit]]

  private val _forUnit: Modifier[Id, Any, Unit] =
    (_, _) => Resource.unit

  given forTuple[F[_], E, M <: Tuple](
      using inst: K0.ProductInstances[Modifier[F, E, _], M]
  ): Modifier[F, E, M] = (m, e) =>
    inst.foldLeft(m)(Resource.unit[F]) {
      [a] => (r: Resource[F, Unit], m: Modifier[F, E, a], a: a) => r *> m.modify(a, e)
    }

  given forOption[F[_], E, A](using M: Modifier[F, E, A]): Modifier[F, E, Option[A]] =
    (as, e) => as.foldMapM(M.modify(_, e)).void

  given forList[F[_], E, A](using M: Modifier[F, E, A]): Modifier[F, E, List[A]] =
    (as, e) => as.foldMapM(M.modify(_, e)).void

  given forResource[F[_], E, A](using M: Modifier[F, E, A]): Modifier[F, E, Resource[F, A]] =
    (a, e) => a.flatMap(M.modify(_, e))

  inline given [F[_], E]: Contravariant[Modifier[F, E, _]] =
    _contravariant.asInstanceOf[Contravariant[Modifier[F, E, _]]]
  private val _contravariant: Contravariant[Modifier[Id, Any, _]] = new:
    def contramap[A, B](fa: Modifier[Id, Any, A])(f: B => A) =
      fa.contramap(f)

  private[html] def forSignal[F[_], E, M, V](signal: M => Signal[F, V])(
      mkModify: (M, E) => V => F[Unit])(using F: Async[F]): Modifier[F, E, M] = (m, e) =>
    signal(m).getAndDiscreteUpdates.flatMap { (head, tail) =>
      val modify = mkModify(m, e)
      Resource.eval(modify(head)) *>
        (F.cede *> tail.foreach(modify(_)).compile.drain).background.void
    }

  private[html] def forSignalResource[F[_], E, M, V](signal: M => Resource[F, Signal[F, V]])(
      mkModify: (M, E) => V => F[Unit])(using F: Async[F]): Modifier[F, E, M] = (m, e) =>
    signal(m).flatMap { sig =>
      sig.getAndDiscreteUpdates.flatMap { (head, tail) =>
        val modify = mkModify(m, e)
        Resource.eval(modify(head)) *>
          (F.cede *> tail.foreach(modify(_)).compile.drain).background.void
      }
    }

private trait Modifiers[F[_]](using F: Async[F]):

  inline given forString[E <: fs2.dom.Node[F]]: Modifier[F, E, String] =
    _forString.asInstanceOf[Modifier[F, E, String]]

  private val _forString: Modifier[F, dom.Node, String] = (s, e) =>
    Resource.eval {
      F.delay {
        e.appendChild(dom.document.createTextNode(s))
        ()
      }
    }

  inline given forStringSignal[E <: fs2.dom.Node[F], S <: Signal[F, String]]
      : Modifier[F, E, S] =
    _forStringSignal.asInstanceOf[Modifier[F, E, S]]

  private val _forStringSignal: Modifier[F, dom.Node, Signal[F, String]] = (s, e) =>
    s.getAndDiscreteUpdates.flatMap { (head, tail) =>
      Resource
        .eval(F.delay(e.appendChild(dom.document.createTextNode(head))))
        .flatMap { n =>
          (F.cede *> tail.foreach(t => F.delay(n.textContent = t)).compile.drain).background
        }
        .void
    }

  inline given forStringOptionSignal[E <: fs2.dom.Node[F], S <: Signal[F, Option[String]]]
      : Modifier[F, E, S] = _forStringOptionSignal.asInstanceOf[Modifier[F, E, S]]

  private val _forStringOptionSignal: Modifier[F, dom.Node, Signal[F, Option[String]]] =
    _forStringSignal.contramap(_.map(_.getOrElse("")))

  inline given forNode[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]: Modifier[F, N, N2] =
    _forNode.asInstanceOf[Modifier[F, N, N2]]

  private val _forNode: Modifier[F, dom.Node, dom.Node] = (n2, n) =>
    Resource.eval(F.delay { n.appendChild(n2); () })

  inline given forNodeResource[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]
      : Modifier[F, N, Resource[F, N2]] =
    _forNodeResource.asInstanceOf[Modifier[F, N, Resource[F, N2]]]

  private val _forNodeResource: Modifier[F, dom.Node, Resource[F, dom.Node]] =
    Modifier.forResource(using _forNode)

  inline given forNodeSignal[
      N <: fs2.dom.Node[F],
      N2 <: fs2.dom.Node[F],
      S <: Signal[F, Resource[F, N2]]
  ]: Modifier[F, N, S] = _forNodeSignal.asInstanceOf[Modifier[F, N, S]]

  private val _forNodeSignal: Modifier[F, dom.Node, Signal[F, Resource[F, dom.Node]]] =
    (n2s, n) =>
      n2s.getAndDiscreteUpdates.flatMap { (head, tail) =>
        DomHotswap(head).flatMap { (hs, n2) =>
          F.delay(n.appendChild(n2)).toResource *>
            (F.cede *> tail
              .foreach(hs.swap(_)((n2, n3) => F.delay { n.replaceChild(n3, n2); () }))
              .compile
              .drain).background
        }.void
      }

  inline given forNodeOptionSignal[
      N <: fs2.dom.Node[F],
      N2 <: fs2.dom.Node[F],
      S <: Signal[F, Option[Resource[F, N2]]]
  ]: Modifier[F, N, S] = _forNodeOptionSignal.asInstanceOf[Modifier[F, N, S]]

  private val _forNodeOptionSignal
      : Modifier[F, dom.Node, Signal[F, Option[Resource[F, dom.Node]]]] = (n2s, n) =>
    Resource.eval(F.delay(Resource.pure[F, dom.Node](dom.document.createComment("")))).flatMap {
      sentinel => _forNodeSignal.modify(n2s.map(_.getOrElse(sentinel)), n)
    }
