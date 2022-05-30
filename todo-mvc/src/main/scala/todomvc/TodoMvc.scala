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
import calico.dsl.io.*
import calico.frp.given
import calico.syntax.*
import cats.effect.*
import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.concurrent.*
import monocle.function.*
import org.scalajs.dom.*

import scala.collection.immutable.SortedMap

object TodoMvc extends IOWebApp:

  def render = (TodoStore.empty, SignallingRef[IO].of(Filter.All)).tupled.toResource.flatMap {
    (store, filter) =>
      div(
        cls := "todoapp",
        div(cls := "header", h1("todos"), TodoInput(store)),
        div(
          cls := "main",
          ul(
            cls := "todo-list",
            children[Int](id => TodoItem(store.entry(id))) <--
              (filter: Signal[IO, Filter]).flatMap(store.ids(_)).discrete.changes
          )
        ),
        store
          .size
          .discrete
          .map(_ > 0)
          .changes
          .map(if _ then StatusBar(store.activeCount, filter).some else None)
      )
  }

  def TodoInput(store: TodoStore) =
    input { self =>
      (
        cls := "new-todo",
        placeholder := "What needs to be done?",
        autoFocus := true,
        onKeyDown --> {
          _.filter(_.keyCode == KeyCode.Enter)
            .mapToTargetValue
            .filterNot(_.isEmpty)
            .foreach(store.create(_) *> IO(self.value = ""))
        }
      )
    }

  def TodoItem(todo: SignallingRef[IO, Option[Todo]]) =
    SignallingRef[IO].of(false).toResource.flatMap { editing =>
      li(
        cls <-- (todo: Signal[IO, Option[Todo]], editing: Signal[IO, Boolean]).mapN { (t, e) =>
          val completed = Option.when(t.exists(_.completed))("completed")
          val editing = Option.when(e)("editing").toList
          completed.toList ++ editing.toList
        },
        onDblClick --> (_.foreach(_ => editing.set(true))),
        children <-- editing.map {
          case true =>
            List(
              input { self =>
                val endEdit = IO(self.value).flatMap { text =>
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
                    _.foreach(_ => todo.update(_.map(_.copy(completed = self.checked))))
                  }
                )
              },
              label(todo.map(_.map(_.text))),
              button(cls := "destroy", onClick --> (_.foreach(_ => todo.set(None))))
            )
        }
      )
    }

  def StatusBar(activeCount: Signal[IO, Int], filter: SignallingRef[IO, Filter]) =
    footer(
      cls := "footer",
      span(
        cls := "todo-count",
        activeCount.map {
          case 1 => "1 item left"
          case n => n.toString + " items left"
        }.discrete // TODO dotty bug
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
                onClick --> (_.foreach(_ => filter.set(f))),
                f.toString
              )
            )
          }
      )
    )

class TodoStore(map: SignallingRef[IO, SortedMap[Int, Todo]], nextId: Ref[IO, Int]):
  def create(text: String): IO[Unit] =
    nextId.getAndUpdate(_ + 1).flatMap(id => map.update(_ + (id -> Todo(text, false))))

  def entry(id: Int): SignallingRef[IO, Option[Todo]] =
    map.zoom(At.atSortedMap[Int, Todo].at(id))

  def ids(filter: Filter): Signal[IO, List[Int]] =
    map.map(_.filter((_, t) => filter.pred(t)).keySet.toList)

  def size: Signal[IO, Int] = map.map(_.size)

  def activeCount: Signal[IO, Int] = map.map(_.values.count(!_.completed))

object TodoStore:
  def empty: IO[TodoStore] = for
    map <- SignallingRef[IO].of(SortedMap.empty[Int, Todo])
    nextId <- IO.ref(0)
  yield TodoStore(map, nextId)

case class Todo(text: String, completed: Boolean)

enum Filter(val pred: Todo => Boolean):
  case All extends Filter(_ => true)
  case Active extends Filter(!_.completed)
  case Completed extends Filter(_.completed)
