package calico

import calico.dsl.io.*

object Example extends IOWebApp:
  def render = div(children := List(p("hello")))
