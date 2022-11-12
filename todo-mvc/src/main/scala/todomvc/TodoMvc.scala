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

package todomvc

import calico.*
import calico.frp.{*, given}
import calico.html.Html
import calico.router.*
import calico.syntax.*
import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.concurrent.*
import fs2.dom.*
import io.circe.Codec
import io.circe.jawn
import io.circe.syntax.*
import org.http4s.*
import org.scalajs.dom.KeyCode

import scala.collection.immutable.SortedMap

object TodoMvc extends IOWebApp:

  def render =
    (TodoStore(Storage.local[IO]), Router(History[IO, Unit]).toResource).flatMapN {
      (store, router) =>
        import calico.html.given
        TodoRouter(store, router)
    }

object TodoRouter:
  def apply[F[_]: Concurrent: Html: Dom](
      store: TodoStore[F],
      router: Router[F]
  ) = router.dispatch {
    Routes.one[F] {
      case uri if uri.fragment == Some("/active") => Filter.Active
      case uri if uri.fragment == Some("/completed") => Filter.Completed
      case _ => Filter.All
    }(TodoList(store, router, _))
  }

object TodoList:
  def apply[F[_]: Concurrent: Dom](
      store: TodoStore[F],
      router: Router[F],
      filter: Signal[F, Filter])(using html: Html[F]) =
    import html.{*, given}
    div(
      cls := "todoapp",
      div(cls := "header", h1("todos"), TodoInput(store)),
      div(
        cls := "main",
        ul(
          cls := "todo-list",
          children[Long](id => TodoItem(store.entry(id))) <-- filter.flatMap(store.ids(_))
        )
      ),
      store
        .size
        .map(_ > 0)
        .changes
        .map(if _ then StatusBar(store.activeCount, filter, router).some else None)
    )

object TodoInput:
  def apply[F[_]: Apply: Dom](store: TodoStore[F])(using html: Html[F]) =
    import html.{*, given}
    input { self =>
      (
        cls := "new-todo",
        placeholder := "What needs to be done?",
        autoFocus := true,
        onKeyDown --> {
          _.filter(_.keyCode == KeyCode.Enter)
            .evalMap(_ => self.value.get)
            .filterNot(_.isEmpty)
            .foreach(store.create(_) *> self.value.set(""))
        }
      )
    }

object TodoItem:
  def apply[F[_]: Concurrent: Dom](todo: SignallingRef[F, Option[Todo]])(using html: Html[F]) =
    import html.{*, given}
    SignallingRef[F].of(false).toResource.flatMap { editing =>
      li(
        cls <-- (todo: Signal[F, Option[Todo]], editing: Signal[F, Boolean]).mapN { (t, e) =>
          val completed = Option.when(t.exists(_.completed))("completed")
          val editing = Option.when(e)("editing")
          completed.toList ++ editing.toList
        },
        onDblClick --> (_.foreach(_ => editing.set(true))),
        children <-- editing.map {
          case true =>
            List(
              input { self =>
                val endEdit = self.value.get.flatMap { text =>
                  todo.update(_.map(_.copy(text = text))) *> editing.set(false)
                }

                (
                  cls := "edit",
                  defaultValue <-- todo.map(_.foldMap(_.text)),
                  onKeyDown --> {
                    _.filter(_.keyCode == KeyCode.Enter).foreach(_ => endEdit)
                  },
                  onBlur --> (_.foreach(_ => endEdit))
                )
              }
            )
          case false =>
            List(
              input { self =>
                (
                  cls := "toggle",
                  typ := "checkbox",
                  checked <-- todo.map(_.fold(false)(_.completed)),
                  onInput --> {
                    _.foreach { _ =>
                      self.checked.get.flatMap { checked =>
                        todo.update(_.map(_.copy(completed = checked)))
                      }
                    }
                  }
                )
              },
              label(todo.map(_.map(_.text))),
              button(cls := "destroy", onClick --> (_.foreach(_ => todo.set(None))))
            )
        }
      )
    }

object StatusBar:
  def apply[F[_]: Functor](
      activeCount: Signal[F, Int],
      filter: Signal[F, Filter],
      router: Router[F])(using html: Html[F]) =
    import html.{*, given}
    footer(
      cls := "footer",
      span(
        cls := "todo-count",
        activeCount.map {
          case 1 => "1 item left"
          case n => n.toString + " items left"
        }
      ),
      ul(
        cls := "filters",
        Filter
          .values
          .toList
          .map { f =>
            li(
              a(
                cls <-- filter.map(_ == f).map(Option.when(_)("selected").toList),
                onClick --> (_.foreach(_ => router.navigate(Uri(fragment = f.fragment.some)))),
                f.toString
              )
            )
          }
      )
    )

trait TodoStore[F[_]]:
  def create(text: String): F[Unit]
  def entry(id: Long): SignallingRef[F, Option[Todo]]
  def ids(filter: Filter): Signal[F, List[Long]]
  def size: Signal[F, Int]
  def activeCount: Signal[F, Int]

object TodoStore:
  def apply[F[_]](storage: Storage[F])(using F: Temporal[F]): Resource[F, TodoStore[F]] =
    val key = "todos-calico"

    for
      entries <- SignallingSortedMapRef[F, Long, Todo].toResource

      _ <- Resource.eval {
        OptionT(storage.getItem(key))
          .subflatMap(jawn.decode[SortedMap[Long, Todo]](_).toOption)
          .foreachF(entries.set(_))
      }

      _ <- storage
        .events
        .foreach {
          case Storage.Event.Updated(`key`, _, value, _) =>
            jawn.decode[SortedMap[Long, Todo]](value).foldMapM(entries.set(_))
          case _ => F.unit
        }
        .compile
        .drain
        .background

      _ <- entries
        .discrete
        .foreach(todos => storage.setItem(key, todos.asJson.noSpaces))
        .compile
        .drain
        .background
    yield new:
      def create(text: String) =
        F.realTime.flatMap(t => entries(t.toMillis).set(Some(Todo(text, false))))

      def entry(id: Long) = entries(id)

      def ids(filter: Filter) =
        entries.map(_.filter((_, t) => filter.pred(t)).keySet.toList)

      def size = entries.map(_.size)

      def activeCount = entries.map(_.values.count(!_.completed))

case class Todo(text: String, completed: Boolean) derives Codec.AsObject

enum Filter(val fragment: String, val pred: Todo => Boolean):
  case All extends Filter("/", _ => true)
  case Active extends Filter("/active", !_.completed)
  case Completed extends Filter("/completed", _.completed)
