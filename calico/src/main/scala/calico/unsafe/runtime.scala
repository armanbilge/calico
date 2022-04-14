package calico.unsafe

import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.IORuntimeConfig
import cats.effect.unsafe.Scheduler

given IORuntime = IORuntime(
  MicrotaskExecutor,
  MicrotaskExecutor,
  Scheduler.createDefaultScheduler()._1,
  () => (),
  IORuntimeConfig()
)
