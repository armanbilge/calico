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

package calico.html.codecs

/**
 * Use this codec when you don't need any data transformation
 */

trait AsIsCodec[T] extends Codec[T, T] {
  override def decode(domValue: T): T = domValue
  override def encode(scalaValue: T): T = scalaValue
}

object AsIsCodec {

  def apply[T]: AsIsCodec[T] = new AsIsCodec[T] {}

  object BooleanAsIsCodec extends AsIsCodec[Boolean]

  object DoubleAsIsCodec extends AsIsCodec[Double]

  object StringAsIsCodec extends AsIsCodec[String]

  // Int Codecs

  object IntAsIsCodec extends AsIsCodec[Int]
}
