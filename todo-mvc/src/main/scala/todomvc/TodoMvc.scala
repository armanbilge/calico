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
import calico.html.io.{*, given}
import calico.router.*
import calico.syntax.*
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
    (TodoStore.make, Router(History[IO, Unit]).toResource).flatMapN { (store, router) =>
      router.dispatch {
        Routes.one[IO] {
          case uri if uri.fragment == Some("/active") => Filter.Active
          case uri if uri.fragment == Some("/completed") => Filter.Completed
          case _ => Filter.All
        } { filter =>
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
        }

      }
    }

  def TodoInput(store: TodoStore) =
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

  def TodoItem(todo: SignallingRef[IO, Option[Todo]]) =
    SignallingRef[IO].of(false).toResource.flatMap { editing =>
      li(
        cls <-- (todo: Signal[IO, Option[Todo]], editing: Signal[IO, Boolean]).mapN { (t, e) =>
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

  def StatusBar(activeCount: Signal[IO, Int], filter: Signal[IO, Filter], router: Router[IO]) =
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

class TodoStore(entries: SignallingSortedMapRef[IO, Long, Todo], nextId: IO[Long]):
  def create(text: String): IO[Unit] =
    nextId.flatMap(entries(_).set(Some(Todo(text, false))))

  def entry(id: Long): SignallingRef[IO, Option[Todo]] = entries(id)

  def ids(filter: Filter): Signal[IO, List[Long]] =
    entries.map(_.filter((_, t) => filter.pred(t)).keySet.toList)

  def size: Signal[IO, Int] = entries.map(_.size)

  def activeCount: Signal[IO, Int] = entries.map(_.values.count(!_.completed))

object TodoStore:

  def make: Resource[IO, TodoStore] =
    val key = "todos-calico"

    for
      mapRef <- SignallingSortedMapRef[IO, Long, Todo].toResource

      _ <- Resource.eval {
        OptionT(Storage.local[IO].getItem(key))
          .subflatMap(jawn.decode[SortedMap[Long, Todo]](_).toOption)
          .foreachF(mapRef.set(_))
      }

      _ <- Storage
        .local[IO]
        .events
        .foreach {
          case Storage.Event.Updated(`key`, _, value, _) =>
            jawn.decode[SortedMap[Long, Todo]](value).foldMapM(mapRef.set(_))
          case _ => IO.unit
        }
        .compile
        .drain
        .background

      _ <- mapRef
        .discrete
        .foreach(todos => Storage.local[IO].setItem(key, todos.asJson.noSpaces))
        .compile
        .drain
        .backgroundOn(calico.unsafe.MacrotaskExecutor)
    yield TodoStore(mapRef, IO.realTime.map(_.toMillis))

case class Todo(text: String, completed: Boolean) derives Codec.AsObject

enum Filter(val fragment: String, val pred: Todo => Boolean):
  case All extends Filter("/", _ => true)
  case Active extends Filter("/active", !_.completed)
  case Completed extends Filter("/completed", _.completed)
