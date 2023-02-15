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

package calico.frp

import cats.effect.kernel.Concurrent
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.kernel.Order
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingMapRef
import fs2.concurrent.SignallingRef

import scala.collection.immutable.LongMap
import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet

abstract class SignallingSortedMapRef[F[_], K, V]
    extends SignallingRef[F, SortedMap[K, V]]
    with SignallingMapRef[F, K, Option[V]]:
  override def apply(k: K): SignallingRef[F, Option[V]]

  def keys: Signal[F, SortedSet[K]]

object SignallingSortedMapRef:
  def apply[F[_], K, V](
      using F: Concurrent[F],
      K: Order[K]): F[SignallingSortedMapRef[F, K, V]] =

    case class State(
        value: SortedMap[K, V],
        lastUpdate: Long,
        listeners: LongMap[Deferred[F, (SortedMap[K, V], Long)]],
        keys: Map[K, KeyState]
    )

    case class KeyState(
        lastUpdate: Long,
        listeners: LongMap[Deferred[F, (Option[V], Long)]]
    )

    given Ordering[K] = K.toOrdering

    F.ref(State(SortedMap.empty, 0L, LongMap.empty, SortedMap.empty)).product(F.ref(1L)).map {
      case (state, ids) =>
        val newId = ids.getAndUpdate(_ + 1)

        def traverse_[A, U](it: Iterable[A])(f: A => F[U]): F[Unit] =
          it.foldLeft(F.unit)(_ <* f(_))

        def incrementLastUpdate(lu: Long) =
          // skip -1 b/c of its special semantic
          if lu == -2L then 0L else lu + 1

        def updateMapAndNotify[O](
            state: State,
            f: SortedMap[K, V] => (SortedMap[K, V], O)): (State, F[O]) =
          val (newValue, result) = f(state.value)

          val lastUpdate = incrementLastUpdate(state.lastUpdate)
          val newKeys = newValue.view.mapValues(_ => KeyState(lastUpdate, LongMap.empty)).toMap
          val newState = State(newValue, lastUpdate, LongMap.empty, newKeys)

          val notifyListeners =
            traverse_(state.listeners.values)(_.complete(newValue -> lastUpdate))
          val notifyKeyListeners = traverse_(state.keys) {
            case (k, KeyState(_, listeners)) =>
              val v = newValue.get(k)
              traverse_(listeners.values)(_.complete(v -> lastUpdate))
          }

          newState -> (notifyListeners *> notifyKeyListeners).as(result)

        def updateKeyAndNotify[U](state: State, k: K, f: Option[V] => (Option[V], U))
            : (State, F[U]) =
          val (newValue, result) = f(state.value.get(k))

          val newMap = newValue.fold(state.value - k)(v => state.value + (k -> v))

          val lastUpdate = incrementLastUpdate(state.lastUpdate)
          val lastKeyUpdate = if newValue.isDefined then lastUpdate else -1L

          val newKeys =
            if newValue.isDefined then
              state.keys.updated(k, KeyState(lastKeyUpdate, LongMap.empty))
            else state.keys - k // prevent memory leak
          val newState = State(newMap, lastUpdate, LongMap.empty, newKeys)

          val notifyListeners =
            traverse_(state.listeners.values)(_.complete(newMap -> lastUpdate))
          val notifyKeyListeners = state.keys.get(k).fold(F.unit) {
            case KeyState(_, listeners) =>
              traverse_(listeners.values)(_.complete(newValue -> lastUpdate))
          }

          newState -> (notifyListeners *> notifyKeyListeners).as(result)

        new SignallingSortedMapRef[F, K, V]
          with AbstractSignallingRef[F, State, SortedMap[K, V]](newId, state):
          outer =>
          def getValue(s: State) = s.value

          def getLastUpdate(s: State) = s.lastUpdate

          def withListener(s: State, id: Long, wait: Deferred[F, (SortedMap[K, V], Long)]) =
            s.copy(listeners = s.listeners.updated(id, wait))

          def withoutListener(s: State, id: Long) = s.copy(listeners = s.listeners.removed(id))

          def updateAndNotify[O](s: State, f: SortedMap[K, V] => (SortedMap[K, V], O)) =
            updateMapAndNotify(s, f)

          def keys = outer.map(_.keySet).changes

          def apply(k: K) = new AbstractSignallingRef[F, State, Option[V]](newId, state):

            def getValue(s: State) = s.value.get(k)

            def getLastUpdate(s: State) = s.keys.get(k).fold(-1L)(_.lastUpdate)

            def withListener(s: State, id: Long, wait: Deferred[F, (Option[V], Long)]) =
              s.copy(keys = s.keys.updatedWith(k) { ks =>
                val lastUpdate = ks.fold(-1L)(_.lastUpdate)
                val listeners = ks.fold(LongMap.empty)(_.listeners).updated(id, wait)
                Some(KeyState(lastUpdate, listeners))
              })

            def withoutListener(s: State, id: Long) =
              s.copy(keys = s.keys.updatedWith(k) {
                _.map(ks => ks.copy(listeners = ks.listeners.removed(id)))
                  // prevent memory leak
                  .filterNot(ks => ks.lastUpdate == -1 && ks.listeners.isEmpty)
              })

            def updateAndNotify[O](s: State, f: Option[V] => (Option[V], O)) =
              updateKeyAndNotify(s, k, f)

    }

  private trait AbstractSignallingRef[F[_], S, A](newId: F[Long], state: Ref[F, S])(
      using F: Concurrent[F])
      extends SignallingRef[F, A]:

    def getValue(s: S): A

    def getLastUpdate(s: S): Long

    def withListener(s: S, id: Long, wait: Deferred[F, (A, Long)]): S

    def withoutListener(s: S, id: Long): S

    def updateAndNotify[B](s: S, f: A => (A, B)): (S, F[B])

    def get: F[A] = state.get.map(getValue(_))

    def continuous: Stream[F, A] = Stream.repeatEval(get)

    def discrete: Stream[F, A] =
      Stream.resource(getAndDiscreteUpdates).flatMap {
        case (a, updates) =>
          Stream.emit(a) ++ updates
      }

    override def getAndDiscreteUpdates(using Concurrent[F]): Resource[F, (A, Stream[F, A])] =
      getAndDiscreteUpdatesImpl

    private def getAndDiscreteUpdatesImpl: Resource[F, (A, Stream[F, A])] =
      def go(id: Long, lastSeen: Long): Stream[F, A] =
        def getNext: F[(A, Long)] =
          F.deferred[(A, Long)].flatMap { wait =>
            state.modify { state =>
              val lastUpdate = getLastUpdate(state)
              if lastUpdate != lastSeen then state -> (getValue(state) -> lastUpdate).pure[F]
              else withListener(state, id, wait) -> wait.get
            }.flatten // cancelable
          }

        Stream.eval(getNext).flatMap {
          case (v, lastUpdate) =>
            Stream.emit(v) ++ go(id, lastSeen = lastUpdate)
        }

      def cleanup(id: Long): F[Unit] = state.update(withoutListener(_, id))

      Resource.eval {
        state.get.map { state =>
          (getValue(state), Stream.bracket(newId)(cleanup).flatMap(go(_, getLastUpdate(state))))
        }
      }

    def set(a: A): F[Unit] = update(_ => a)

    def update(f: A => A): F[Unit] = modify(v => (f(v), ()))

    def modify[B](f: A => (A, B)): F[B] =
      state.flatModify(updateAndNotify(_, f))

    def tryModify[B](f: A => (A, B)): F[Option[B]] =
      state.tryModify(updateAndNotify(_, f)).flatMap(_.sequence).uncancelable

    def tryUpdate(f: A => A): F[Boolean] =
      tryModify(a => (f(a), ())).map(_.isDefined)

    def access: F[(A, A => F[Boolean])] =
      state.access.map {
        case (state, set) =>
          val setter = { (newValue: A) =>
            val (newState, notifyListeners) =
              updateAndNotify(state, _ => (newValue, ()))

            set(newState).flatTap { succeeded => notifyListeners.whenA(succeeded) }
          }

          (getValue(state), setter)
      }

    def tryModifyState[B](state: cats.data.State[A, B]): F[Option[B]] = {
      val f = state.runF.value
      tryModify(f(_).value)
    }

    def modifyState[B](state: cats.data.State[A, B]): F[B] = {
      val f = state.runF.value
      modify(f(_).value)
    }
