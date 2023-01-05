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

package calico.html.defs.eventProps

import calico.html.keys.EventProp
import org.scalajs.dom

// #NOTE: GENERATED CODE
//  - This file is generated at compile time from the data in Scala DOM Types
//  - See `project/src/main/scala/calico/html/codegen/DomDefsGenerator.scala` for code generation params
//  - Contribute to https://github.com/raquo/scala-dom-types to add missing tags / attrs / props / etc.

/** Document-only Events */
trait DocumentEventProps[F[_]] { this: GlobalEventProps[F] =>




  // -- Document-only Events --


  /**
    * The `DOMContentLoaded` event is fired when the initial HTML document has been completely loaded and parsed,
    * without waiting for stylesheets, images, and subframes to finish loading. A very different event `load`
    * should be used only to detect a fully-loaded page.
    * 
    * It is an incredibly common mistake to use load where DOMContentLoaded would be much more appropriate,
    * so be cautious.
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Document/DOMContentLoaded_event
    */
  lazy val onDomContentLoaded: EventProp[F, dom.Event] = eventProp("DOMContentLoaded")


  /**
    * The fullscreenchange event is fired immediately after the browser switches into or out of full-screen mode.
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Document/fullscreenchange_event
    */
  lazy val onFullScreenChange: EventProp[F, dom.Event] = eventProp("fullscreenchange")


  /**
    * The fullscreenerror event is fired when the browser cannot switch to full-screen mode.
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Document/fullscreenerror_event
    */
  lazy val onFullScreenError: EventProp[F, dom.Event] = eventProp("fullscreenerror")


  /**
    * The visibilitychange event is fired when the content of a tab has become visible or has been hidden.
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilitychange_event
    */
  lazy val onVisibilityChange: EventProp[F, dom.Event] = eventProp("visibilitychange")


}
