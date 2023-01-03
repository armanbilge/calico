package calico.html

import calico.syntax.*
import calico.util.DomHotswap
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.syntax.all.*
import cats.Foldable
import cats.syntax.all.*
import fs2.concurrent.Signal
import org.scalajs.dom

trait Modifier[F[_], E, A]:
  outer =>

  def modify(a: A, e: E): Resource[F, Unit]

  inline final def contramap[B](inline f: B => A): Modifier[F, E, B] =
    (b: B, e: E) => outer.modify(f(b), e)

object Modifier:
  def forSignal[F[_]: Async, A, B, C](setter: (A, B, C) => F[Unit])(
      signal: B => Signal[F, C]): Modifier[F, A, B] = (b, a) =>
    signal(b).getAndUpdates.flatMap { (head, tail) =>
      def set(c: C) = setter(a, b, c)
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

trait Modifiers[F[_]](using F: Async[F]):
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

  inline given forStringSignal[E <: fs2.dom.Node[F]]: Modifier[F, E, Signal[F, String]] =
    _forStringSignal.asInstanceOf[Modifier[F, E, Signal[F, String]]]

  private val _forStringSignal: Modifier[F, dom.Node, Signal[F, String]] = (s, e) =>
    s.getAndUpdates.flatMap { (head, tail) =>
      Resource
        .eval(F.delay(e.appendChild(dom.document.createTextNode(head))))
        .flatMap { n =>
          tail.foreach(t => F.delay(n.textContent = t)).compile.drain.cedeBackground
        }
        .void
    }

  inline given forStringOptionSignal[E <: fs2.dom.Node[F]]
      : Modifier[F, E, Signal[F, Option[String]]] =
    _forStringOptionSignal.asInstanceOf[Modifier[F, E, Signal[F, Option[String]]]]

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

  inline given forNodeSignal[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]
      : Modifier[F, N, Signal[F, Resource[F, N2]]] =
    _forNodeSignal.asInstanceOf[Modifier[F, N, Signal[F, Resource[F, N2]]]]

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

  inline given forNodeOptionSignal[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]
      : Modifier[F, N, Signal[F, Option[Resource[F, N2]]]] =
    _forNodeOptionSignal.asInstanceOf[Modifier[F, N, Signal[F, Option[Resource[F, N2]]]]]

  private val _forNodeOptionSignal
      : Modifier[F, dom.Node, Signal[F, Option[Resource[F, dom.Node]]]] = (n2s, n) =>
    Resource.eval(F.delay(Resource.pure[F, dom.Node](dom.document.createComment("")))).flatMap {
      sentinel => _forNodeSignal.modify(n2s.map(_.getOrElse(sentinel)), n)
    }
