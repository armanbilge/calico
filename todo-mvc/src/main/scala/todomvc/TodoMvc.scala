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
import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import fs2.concurrent.*
import fs2.dom.*
import io.circe
import io.circe.Decoder.Result
import io.circe.*
import io.circe.syntax.*
import org.http4s.*
import org.scalajs.dom.KeyValue

import scala.collection.immutable.SortedMap

object TodoMvc extends IOWebApp:

  def render = (TodoStore(window), Router(window).toResource).flatMapN { (store, router) =>
    router.dispatch {
      Routes.one[IO] {
        case uri if uri.fragment == Some("/active") => Filter.Active
        case uri if uri.fragment == Some("/completed") => Filter.Completed
        case _ => Filter.All
      } { filter =>
        div(
          cls := "todoapp",
          headerTag(cls := "header", h1("todos"), TodoInput(store)),
          sectionTag(
            cls := "main",
            input.withSelf(self =>
              (
                idAttr := "toggle-all",
                cls := "toggle-all",
                typ := "checkbox",
                checked <-- store.allCompleted,
                onInput --> { _.foreach(_ => self.checked.get.flatMap(store.toggleAll)) })),
            label(forId := "toggle-all", "Mark all as complete"),
            ul(
              cls := "todo-list",
              children[Long](id => TodoItem(store.entry(id))) <-- filter.flatMap(store.ids(_))
            )
          ),
          store
            .size
            .map(_ > 0)
            .changes
            .map(if _ then StatusBar(store, filter, router).some else None)
        )
      }
    }
  }

  def TodoInput(store: TodoStore): Resource[IO, HtmlInputElement[IO]] =
    input.withSelf { self =>
      (
        cls := "new-todo",
        placeholder := "What needs to be done?",
        autoFocus := true,
        onKeyDown --> {
          _.filter(_.key == KeyValue.Enter)
            .evalMap(_ => self.value.get)
            .filterNot(_.isEmpty)
            .foreach(store.create(_) *> self.value.set(""))
        }
      )
    }

  def TodoItem(todo: SignallingRef[IO, Option[Todo]]): Resource[IO, HtmlLiElement[IO]] =
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
              input.withSelf { self =>
                val endEdit = self.value.get.map(_.trim).flatMap { text =>
                  todo.update(t =>
                    text match {
                      case "" => None
                      case _ => t.map(_.copy(text = text.trim))
                    })
                } *> editing.set(false)
                (
                  cls := "edit",
                  defaultValue <-- todo.map(_.foldMap(_.text)),
                  onKeyDown --> {
                    _.foreach {
                      case e if e.key == KeyValue.Enter => endEdit
                      case e if e.key == KeyValue.Escape => editing.set(false)
                      case _ => IO.unit
                    }
                  },
                  onBlur --> (_.foreach(_ => {
                    editing
                      .get
                      .flatMap(
                        IO.whenA(_)(endEdit)
                      ) // do not endEdit when blur is triggered after Escape
                  }))
                )
              }
            )
          case false =>
            List(div(
              cls := "view",
              input.withSelf { self =>
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
            ))
        }
      )
    }

  def StatusBar(
      store: TodoStore,
      filter: Signal[IO, Filter],
      router: Router[IO]
  ): Resource[IO, HtmlElement[IO]] =
    footerTag(
      cls := "footer",
      span(
        cls := "todo-count",
        strong(store.activeCount.map(_.toString)),
        store.activeCount.map {
          case 1 => " item left"
          case n => " items left"
        }
      ),
      ul(
        cls := "filters",
        Filter.values.toList.map { f =>
          li(
            a(
              cls <-- filter.map(_ == f).map(Option.when(_)("selected").toList),
              onClick --> (_.foreach(_ => router.navigate(Uri(fragment = f.fragment.some)))),
              href := s"/#${f.fragment}",
              f.toString
            )
          )
        }
      ),
      store
        .hasCompleted
        .map(
          Option.when(_)(
            button(
              cls := "clear-completed",
              onClick --> {
                _.foreach(_ => store.clearCompleted)
              },
              "Clear completed")))
    )

class TodoStore(entries: SignallingSortedMapRef[IO, Long, Todo], nextId: IO[Long]):
  def toggleAll(completed: Boolean): IO[Unit] =
    entries.update(_.map((id, todo) => (id, todo.copy(completed = completed))))

  def allCompleted: Signal[IO, Boolean] = entries.map(_.values.forall(_.completed))

  def hasCompleted: Signal[IO, Boolean] = entries.map(_.values.exists(_.completed))

  def clearCompleted: IO[Unit] = entries.update(_.filterNot((_, todo) => todo.completed))

  def create(text: String): IO[Unit] =
    nextId.flatMap(entries(_).set(Some(Todo(text, false))))

  def entry(id: Long): SignallingRef[IO, Option[Todo]] = entries(id)

  def ids(filter: Filter): Signal[IO, List[Long]] =
    entries.map(_.filter((_, t) => filter.pred(t)).keySet.toList)

  def size: Signal[IO, Int] = entries.map(_.size)

  def activeCount: Signal[IO, Int] = entries.map(_.values.count(!_.completed))

object TodoStore:

  def apply(window: Window[IO]): Resource[IO, TodoStore] =
    val key = "todos-calico"

    implicit val encodeFoo: Encoder[(Long, Todo)] = new Encoder[(Long, Todo)] {
      override def apply(a: (Long, Todo)): Json = {
        val (id, todo) = a
        Json.obj(
          ("id", Json.fromLong(id)),
          ("title", Json.fromString(todo.text)),
          ("completed", Json.fromBoolean(todo.completed))
        )
      }
    }

    implicit val decodeFoo: Decoder[(Long, Todo)] = new Decoder[(Long, Todo)] {
      override def apply(c: HCursor): Result[(Long, Todo)] = for {
        id <- c.downField("id").as[Long]
        title <- c.downField("title").as[String]
        completed <- c.downField("completed").as[Boolean]
      } yield {
        (id, Todo(title, completed))
      }
    }

    for
      mapRef <- SignallingSortedMapRef[IO, Long, Todo].toResource

      _ <- Resource.eval {
        OptionT(window.localStorage.getItem(key))
          .subflatMap(circe.jawn.decode[List[(Long, Todo)]](_).toOption.map(SortedMap.from))
          .foreachF(mapRef.set(_))
      }

//      _ <- window
//        .localStorage
//        .events(window)
//        .foreach {
//          case Storage.Event.Updated(`key`, _, value, _) =>
//            jawn.decode[SortedMap[Long, Todo]](value).foldMapM(mapRef.set(_))
//          case _ => IO.unit
//        }
//        .compile
//        .drain
//        .background

      _ <- mapRef
        .discrete
        .foreach((todos: Map[Long, Todo]) =>
          IO.cede *> window
            .localStorage
            .setItem(
              key,
              todos.toList.asJson.noSpaces
            ))
        .compile
        .drain
        .background
    yield new TodoStore(mapRef, IO.realTime.map(_.toMillis))

case class Todo(text: String, completed: Boolean) derives Codec.AsObject

enum Filter(val fragment: String, val pred: Todo => Boolean):
  case All extends Filter("/", _ => true)
  case Active extends Filter("/active", !_.completed)
  case Completed extends Filter("/completed", _.completed)
