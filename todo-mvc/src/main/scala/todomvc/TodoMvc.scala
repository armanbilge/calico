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
import calico.syntax.*
import cats.effect.*
import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.concurrent.*
import monocle.*
import monocle.macros.GenLens
import org.scalajs.dom.*

import scala.collection.immutable.SortedMap

object TodoMvc extends IOWebApp:

  def render = TodoStore.empty.toResource.flatMap { store =>
    div(
      cls := "todoapp",
      div(cls := "header", h1("todos"), TodoInput(store)),
      div(
        cls := "main",
        ul(
          cls := "todo-list",
          children[Int, HTMLElement]((id: Int) => TodoItem(???, store.delete(id))) <-- store
            .ids
            .discrete
        )
      )
    )
  }

  def TodoInput(store: TodoStore) =
    input { self =>
      (
        cls := "new-todo",
        placeholder := "What needs to be done?",
        autoFocus := true,
        onKeyPress --> {
          _.filter(_.keyCode == KeyCode.Enter).foreach { _ =>
            store.create(self.value) *> IO(self.value = "")
          }
        }
      )
    }

  def TodoItem(todo: SignallingRef[IO, Todo], delete: IO[Unit]) =
    SignallingRef[IO].of(false).toResource.flatMap { editing =>
      li(
        cls <-- todo.discrete.map(t => Option.when(t.completed)("completed").toList),
        onDblClick --> (_.foreach(_ => editing.set(true))),
        children[HTMLElement] <-- editing.discrete.map {
          case true =>
            List(
              input { self =>
                (
                  cls := "edit",
                  defaultValue <-- todo.discrete.map(_.text),
                  onKeyPress --> {
                    _.filter(_.keyCode == KeyCode.Enter).foreach { _ =>
                      todo.update(_.copy(text = self.value))
                    }
                  },
                  onBlur --> (_.foreach(_ => todo.update(_.copy(text = self.value))))
                )
              }
            )
          case false =>
            List(
              input { self =>
                (
                  cls := "toggle",
                  typ := "checkbox",
                  checked <-- todo.discrete.map(_.completed),
                  onInput --> (_.foreach(_ => todo.update(_.copy(completed = self.checked))))
                )
              },
              label(todo.discrete.map(_.text)),
              button(cls := "destroy", onClick --> (_.foreach(_ => delete)))
            )
        }
      )
    }

class TodoStore(map: SignallingRef[IO, SortedMap[Int, Todo]], nextId: Ref[IO, Int]):
  def create(text: String): IO[Unit] =
    nextId.getAndUpdate(_ + 1).flatMap(id => map.update(_ + (id -> Todo(text, false))))

  def delete(id: Int): IO[Unit] = map.update(_ - id)

  def ids: Signal[IO, List[Int]] = map.map(_.keySet.toList)

object TodoStore:
  def empty: IO[TodoStore] = for
    map <- SignallingRef[IO].of(SortedMap.empty[Int, Todo])
    nextId <- IO.ref(0)
  yield TodoStore(map, nextId)

case class Todo(text: String, completed: Boolean)
