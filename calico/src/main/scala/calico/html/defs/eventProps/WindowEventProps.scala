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

/** Window-only Events */
trait WindowEventProps[F[_]] { this: GlobalEventProps[F] =>




  // -- Window-only Events --


  /**
    * Script to be run after the document is printed
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/afterprint_event
    */
  lazy val onAfterPrint: EventProp[F, dom.Event] = eventProp("afterprint")


  /**
    * Script to be run before the document is printed
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeprint_event
    */
  lazy val onBeforePrint: EventProp[F, dom.Event] = eventProp("beforeprint")


  /**
    * Script to be run when the document is about to be unloaded
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeunload_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/BeforeUnloadEvent
    */
  lazy val onBeforeUnload: EventProp[F, dom.BeforeUnloadEvent] = eventProp("beforeunload")


  /**
    * Script to be run when there has been changes to the anchor part of the a URL
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/hashchange_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/HashChangeEvent
    */
  lazy val onHashChange: EventProp[F, dom.HashChangeEvent] = eventProp("hashchange")


  /**
    * Script to be run when an object receives a message
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/message_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/MessageEvent
    */
  lazy val onMessage: EventProp[F, dom.MessageEvent] = eventProp("message")


  /**
    * Script to be run when an object receives a message that cannot be
    * deserialized and therefore raises an error
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/messageerror_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/MessageEvent
    */
  lazy val onMessageError: EventProp[F, dom.MessageEvent] = eventProp("messageerror")


  /**
    * Script to be run when the browser starts to work offline
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/offline_event
    */
  lazy val onOffline: EventProp[F, dom.Event] = eventProp("offline")


  /**
    * Script to be run when the browser starts to work online
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/online_event
    */
  lazy val onOnline: EventProp[F, dom.Event] = eventProp("online")


  /**
    * Script to be run when a user navigates away from a page
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/pagehide_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/PageTransitionEvent
    */
  lazy val onPageHide: EventProp[F, dom.PageTransitionEvent] = eventProp("pagehide")


  /**
    * Script to be run when a user navigates to a page
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/pageshow_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/PageTransitionEvent
    */
  lazy val onPageShow: EventProp[F, dom.PageTransitionEvent] = eventProp("pageshow")


  /**
    * Script to be run when the window's history changes
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/popstate_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/PopStateEvent
    */
  lazy val onPopState: EventProp[F, dom.PopStateEvent] = eventProp("popstate")


  /**
    * Script to be run when a Web Storage area is updated
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/storage_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/StorageEvent
    */
  lazy val onStorage: EventProp[F, dom.StorageEvent] = eventProp("storage")


  /**
    * Fires once a page has unloaded (or the browser window has been closed)
    * 
    * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/unload_event
    * @see https://developer.mozilla.org/en-US/docs/Web/API/UIEvent
    */
  lazy val onUnload: EventProp[F, dom.UIEvent] = eventProp("unload")


}
