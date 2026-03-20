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

package calico.html
trait ValidAttr[A, -E]

object ValidAttr:
  private val _instance: ValidAttr[Any, Any] = new ValidAttr[Any, Any] {}
  inline def instance[A, E]: ValidAttr[A, E] = _instance.asInstanceOf[ValidAttr[A, E]]

trait ValidProp[A, -E]

object ValidProp:
  private val _instance: ValidProp[Any, Any] = new ValidProp[Any, Any] {}
  inline def instance[A, E]: ValidProp[A, E] = _instance.asInstanceOf[ValidProp[A, E]]
