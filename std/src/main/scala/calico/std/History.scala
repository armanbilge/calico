package calico.std

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import io.circe.Decoder
import io.circe.Encoder
import org.scalajs.dom.URL

trait History[F[_], S]:

  def forward: F[Unit]
  def back: F[Unit]
  def go: F[Unit]
  def go(delta: Int): F[Unit]

  def pushState(state: S): F[Unit]
  def pushState(state: S, url: URL): F[Unit]

  def replaceState(state: S): F[Unit]
  def replaceState(state: S, url: URL): F[Unit]

object History:
  def make[F[_]: Async, A: Decoder: Encoder]: Resource[F, History[F, A]] = ???
