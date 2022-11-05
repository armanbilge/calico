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

package calico.unsafe

import scala.concurrent.ExecutionContextExecutor
import scala.scalajs.js

private[calico] object BatchingMacrotaskExecutor extends ExecutionContextExecutor:

  private val tasks = new js.Array[Runnable]
  private var needsReschedule = true
  private lazy val executeTask: Runnable = () => {
    var i = 0
    while tasks.length > 0 && i < 64 do
      tasks.shift().run()
      i += 1
    if tasks.length > 0 then MacrotaskExecutor.execute(executeTask)
    else needsReschedule = true
  }

  def reportFailure(cause: Throwable): Unit =
    cause.printStackTrace()

  def execute(command: Runnable): Unit =
    tasks.push(command)
    if needsReschedule then
      needsReschedule = false
      MacrotaskExecutor.execute(executeTask)
