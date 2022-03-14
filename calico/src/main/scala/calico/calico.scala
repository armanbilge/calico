package calico

import org.scalajs.dom
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.syntax.all.*

extension [F[_]](component: Resource[F, dom.HTMLElement])
  def renderInto(root: dom.Element)(using F: Sync[F]): Resource[F, Unit] =
    component.flatMap { e =>
      Resource.make(F.delay(root.appendChild(e)))(_ => F.delay(root.removeChild(e))).void
    }
