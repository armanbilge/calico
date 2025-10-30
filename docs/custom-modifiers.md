# Custom Modifiers

Calico provides a powerful typeclass-based modifier system that allows you to create custom modifiers for HTML elements. However, implementing custom modifiers using the `Modifier[F, E, A]` typeclass directly can be verbose and not straightforward.

## The Problem

Previously, to create a custom modifier, you had to implement the `Modifier[F, E, A]` typeclass:

```scala
case class DataAttribute(value: String)

given Modifier[IO, dom.Element, DataAttribute] = (attr, elem) =>
  Resource.eval(IO.delay(elem.setAttribute("data-custom", attr.value)))

// Usage
div(DataAttribute("hello"), "Content")
```

This approach requires:
- Understanding the typeclass system
- Writing boilerplate code
- Managing the `Resource[F, Unit]` return type manually

## The Solution: CustomModifier Trait

Calico now provides the `CustomModifier[F, E]` trait that makes implementing custom modifiers much easier:

```scala
case class DataAttribute(value: String) extends CustomModifier[IO, dom.Element]:
  def apply(element: dom.Element): Resource[IO, Unit] =
    Resource.eval(IO.delay(element.setAttribute("data-custom", value)))

// Usage
div(DataAttribute("hello"), "Content")
```

## Benefits

1. **Simpler API**: Just extend the trait and implement `apply`
2. **Automatic typeclass derivation**: No need to write `given` instances
3. **Better readability**: The intent is clearer
4. **Less boilerplate**: Focus on the actual logic

## Examples

### Basic Custom Modifier

```scala
case class CustomClass(className: String) extends CustomModifier[IO, dom.Element]:
  def apply(element: dom.Element): Resource[IO, Unit] =
    Resource.eval(IO.delay(element.classList.add(className)))

// Usage
div(CustomClass("my-class"), "Hello")
```

### Using the Sync Helper

For simple synchronous operations, you can use the `sync` helper method:

```scala
case class SimpleAttribute(name: String, value: String) extends CustomModifier[IO, dom.Element]:
  def apply(element: dom.Element): Resource[IO, Unit] =
    sync(element)(_.setAttribute(name, value))

// Usage
div(SimpleAttribute("data-id", "123"), "Content")
```

### Complex Logic

You can implement complex logic in your custom modifiers:

```scala
case class ConditionalModifier(condition: Boolean, value: String) extends CustomModifier[IO, dom.Element]:
  def apply(element: dom.Element): Resource[IO, Unit] =
    if condition then
      Resource.eval(IO.delay(element.setAttribute("data-active", value)))
    else
      Resource.eval(IO.delay(element.removeAttribute("data-active")))

// Usage
div(ConditionalModifier(true, "enabled"), "Active content")
```

### Combining with Other Modifiers

Custom modifiers work seamlessly with built-in modifiers:

```scala
div(
  cls := "container",
  CustomClass("highlight"),
  SimpleAttribute("data-role", "main"),
  "Content"
)
```

## Migration Guide

If you have existing custom modifiers using the old approach, migration is straightforward:

**Before:**
```scala
case class MyModifier(value: String)
given Modifier[IO, dom.Element, MyModifier] = (modifier, element) =>
  Resource.eval(IO.delay(element.setAttribute("data-value", modifier.value)))
```

**After:**
```scala
case class MyModifier(value: String) extends CustomModifier[IO, dom.Element]:
  def apply(element: dom.Element): Resource[IO, Unit] =
    Resource.eval(IO.delay(element.setAttribute("data-value", value)))
```

The functionality remains the same, but the code is cleaner and more maintainable.
