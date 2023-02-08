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

import calico.syntax.*
import cats.Contravariant
import cats.Foldable
import cats.Id
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.concurrent.Signal
import org.scalajs.dom

trait Modifier[F[_], E, A]:
  outer =>

  def modify(a: A, e: E): Resource[F, Unit]

  inline final def contramap[B](inline f: B => A): Modifier[F, E, B] =
    (b: B, e: E) => outer.modify(f(b), e)

object Modifier:
  inline given [F[_], E]: Contravariant[Modifier[F, E, _]] =
    _contravariant.asInstanceOf[Contravariant[Modifier[F, E, _]]]
  private val _contravariant: Contravariant[Modifier[Id, Any, _]] = new:
    def contramap[A, B](fa: Modifier[Id, Any, A])(f: B => A) =
      fa.contramap(f)

  private[html] def forSignal[F[_]: Async, E, M, V](signal: M => Signal[F, V])(
      mkModify: (M, E) => V => F[Unit]): Modifier[F, E, M] = (m, e) =>
    signal(m).getAndUpdates.flatMap { (head, tail) =>
      val modify = mkModify(m, e)
      Resource.eval(modify(head)) *>
        tail.foreach(modify(_)).compile.drain.cedeBackground.void
    }

  private[html] def forSignalResource[F[_]: Async, E, M, V](
      signal: M => Resource[F, Signal[F, V]])(
      mkModify: (M, E) => V => F[Unit]): Modifier[F, E, M] = (m, e) =>
    signal(m).flatMap { sig =>
      sig.getAndUpdates.flatMap { (head, tail) =>
        val modify = mkModify(m, e)
        Resource.eval(modify(head)) *>
          tail.foreach(modify(_)).compile.drain.cedeBackground.void
      }
    }

private trait Modifiers[F[_]](using F: Async[F]):
  inline given forUnit[E]: Modifier[F, E, Unit] =
    _forUnit.asInstanceOf[Modifier[F, E, Unit]]

  private val _forUnit: Modifier[F, Any, Unit] =
    (_, _) => Resource.unit

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
    s.getAndUpdates.flatMap { (head, tail) =>
      Resource
        .eval(F.delay(e.appendChild(dom.document.createTextNode(head))))
        .flatMap { n =>
          tail.foreach(t => F.delay(n.textContent = t)).compile.drain.cedeBackground
        }
        .void
    }

  inline given forStringOptionSignal[E <: fs2.dom.Node[F], S <: Signal[F, Option[String]]]
      : Modifier[F, E, S] = _forStringOptionSignal.asInstanceOf[Modifier[F, E, S]]

  private val _forStringOptionSignal: Modifier[F, dom.Node, Signal[F, Option[String]]] =
    _forStringSignal.contramap(_.map(_.getOrElse("")))

  given forResource[E <: fs2.dom.Node[F], A](
      using M: Modifier[F, E, A]): Modifier[F, E, Resource[F, A]] =
    (a, e) => a.flatMap(M.modify(_, e))

  given forFoldable[E <: fs2.dom.Node[F], G[_]: Foldable, A](
      using M: Modifier[F, E, A]): Modifier[F, E, G[A]] =
    (ga, e) => ga.foldMapM(M.modify(_, e)).void

  inline given forNode[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]
      : Modifier[F, N, Resource[F, N2]] =
    _forNode.asInstanceOf[Modifier[F, N, Resource[F, N2]]]

  private val _forNode: Modifier[F, dom.Node, Resource[F, dom.Node]] = (n2, n) =>
    n2.evalMap(n2 => F.delay(n.appendChild(n2)))

  inline given forNodeSignal[
      N <: fs2.dom.Node[F],
      N2 <: fs2.dom.Node[F],
      S <: Signal[F, Resource[F, N2]]
  ]: Modifier[F, N, S] = _forNodeSignal.asInstanceOf[Modifier[F, N, S]]

  private val _forNodeSignal: Modifier[F, dom.Node, Signal[F, Resource[F, dom.Node]]] =
    (n2s, n) =>
      n2s.getAndUpdates.flatMap { (head, tail) =>
        DomHotswap(head).flatMap { (hs, n2) =>
          F.delay(n.appendChild(n2)).toResource *>
            tail
              .foreach(hs.swap(_)((n2, n3) => F.delay(n.replaceChild(n3, n2))))
              .compile
              .drain
              .cedeBackground
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
