package calico.html.tags

import calico.html.Modifier
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.syntax.all.*
import cats.syntax.all.*
import org.scalajs.dom
import shapeless3.deriving.K0

final class HtmlTag[F[_], E <: fs2.dom.HtmlElement[F]] private[calico] (
    name: String,
    void: Boolean)(using F: Async[F]):

  def apply[M](modifier: M)(using M: Modifier[F, E, M]): Resource[F, E] =
    build.toResource.flatTap(M.modify(modifier, _))

  def apply[M](mkModifier: E => M)(using M: Modifier[F, E, M]): Resource[F, E] =
    build.toResource.flatTap(e => M.modify(mkModifier(e), e))

  def apply[M <: Tuple](modifiers: M)(
      using inst: K0.ProductInstances[Modifier[F, E, _], M]): Resource[F, E] =
    inst.foldLeft(modifiers)(build.toResource) {
      [a] => (r: Resource[F, E], m: Modifier[F, E, a], a: a) => r.flatTap(m.modify(a, _))
    }

  def apply[M <: Tuple](mkModifiers: E => M)(
      using inst: K0.ProductInstances[Modifier[F, E, _], M]): Resource[F, E] =
    build.toResource.flatTap { e =>
      inst.foldLeft(mkModifiers(e))(Resource.pure(e)) {
        [a] => (r: Resource[F, E], m: Modifier[F, E, a], a: a) => r.flatTap(m.modify(a, _))
      }
    }

  private def build = F.delay(dom.document.createElement(name).asInstanceOf[E])


