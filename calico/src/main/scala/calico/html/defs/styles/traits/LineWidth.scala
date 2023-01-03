package calico.html.defs.styles.traits

import calico.html.keys.StyleProp
import calico.html.modifiers.KeySetter.StyleSetter
import calico.html.defs.styles.{units => u}
import calico.html.keys.DerivedStyleProp

// #NOTE: GENERATED CODE
//  - This file is generated at compile time from the data in Scala DOM Types
//  - See `project/DomDefsGenerator.scala` for code generation params
//  - Contribute to https://github.com/raquo/scala-dom-types to add missing tags / attrs / props / etc.

trait LineWidth extends u.Length[DerivedStyleProp, Int] { this: StyleProp[_] =>

  /** Typically 1px in desktop browsers like Firefox. */
  lazy val thin: StyleSetter = this := "thin"

  /** Typically 3px in desktop browsers like Firefox. */
  lazy val medium: StyleSetter = this := "medium"

  /** Typically 5px in desktop browsers like Firefox. */
  lazy val thick: StyleSetter = this := "thick"

}
