---
title: Classes & Functions
temper-extract: false
---

# Classes & Functions

Temper enables rich type definitions for use from different languages. It does
this through class and interface definitions as well as plans for enums in the
future (see [issue#50](https://github.com/temperlang/temper/issues/50)). Temper also enables rich behavior with both class-level
methods and top-level functions. Let's learn about it!

!!! note

    These tutorials assume some programming experience. The focus is on Temper syntax and semantics rather than teaching a first programming language.


## Classes with properties

Imagine we want to build a library for managing simple geometric shapes. Such a
library might be useful in a variety of graphical applications.

But before we build more libraries, let's learn some **Temper basics**,
and for that, it will be easier to use an interactive console.
Start up the Temper read-evaluate-print-loop (REPL) console like so:

```sh
temper repl
# And wait for the `$` prompt.
```

If needed, see also the tutorial on [getting started with Temper](./index.md).

The Temper REPL evaluates arbitrary Temper code, including simple math:

```temper
$ 1 + 2
interactive#0: 3
$ 1 + 2;
interactive#1: void
```

Note, above, a trailing semicolon evaluates as `void`.

Now that we've seen how the REPL works, copy and paste the following code into
it:

```temper
class Rectangle(
  // Currently, the only float type in Temper is Float64.
  public width: Float64,
  public height: Float64,
) {}
```

Hitting enter evaluates the class definition in the Temper interpreter, which
is separate from any backend language (such as Java, JS, or Python). You should
see output such as the following:

```temper
interactive#2: Rectangle__0/*new*/
```

Now we can create new `Rectangle` instances:

```temper
$ new Rectangle(1.5, 0.5)
interactive#3: {class: Rectangle__0, width: 1.5, height: 0.5}
```

The numeric suffixes, such as `__0`, provide internally unique names for
diagnosis but aren't the publicly usable names, so continue to exclude them when
providing new inputs.

On the other hand, the JSON-like representation we see above *is* supported by
Temper as an alternative for calling class constructors. Reminiscent of JS
object literals, this is the only form of named arguments provided in Temper.
We call this syntax "property bags":

```temper
// Feel free to try out this form.
{ class: Rectangle, width: 1.5, height: 0.5 }
// If property names identify a unique constructor, the class name is optional.
{ width: 1.5, height: 0.5 }
```

And to assist in backend languages without named arguments (such as Java or
Rust), we generate builders to improve the user experience of Temper-built
libraries. If something feels usable in Temper, it should also be usable in
backend languages. Temper is for making cross-language libraries, after all.
For example, here's a builder generated for Java for the Rectangle class:

```java
public static final class Builder {
    double width;
    boolean width__set;
    public Builder width(double width) {
        width__set = true;
        this.width = width;
        return this;
    }
    double height;
    boolean height__set;
    public Builder height(double height) {
        height__set = true;
        this.height = height;
        return this;
    }
    public Rectangle build() {
        if (!width__set || !height__set) {
            StringBuilder _message = new StringBuilder("Missing required fields:");
            if (!width__set) {
                _message.append(" width");
            }
            if (!height__set) {
                _message.append(" height");
            }
            throw new IllegalStateException(_message.toString());
        }
        return new Rectangle(width, height);
    }
}
```

This allows scaling usability to large numbers of constructor properties.

Back to Temper itself, if we assign an instance to a variable, we can then work
with the properties.

```temper
$ let box = { width: 1.5, height: 0.5 };
interactive#6: void
$ box.width + box.height
interactive#7: 2.0
```

We can also destructure the values into local variables:

```temper
// Note that nested destructuring isn't supported yet, but renaming works.
$ let { width, height as h } = box;
interactive#8: void
$ width
interactive#9: 1.5
$ h
interactive#10: 0.5
$ { height: h * 2.5, width } // shorthand "punning" for `width: width`
interactive#11: {class: Rectangle__0, width: 1.5, height: 1.25}
$ [width, h] // `List` literal, a type we'll see more of later
interactive#12: [1.5,0.5]
```

Also, note that variables defined with `let` can't be reassigned.

```temper
$ h = 1.0;
1: h = 1.0;
   ⇧
[interactive#13:1+0-1]: Cannot set const h__3 again
interactive#13: fail
```

To allow reassignment, use `var` instead of `let` (although `var` doesn't
work across different entries in the REPL). We could also define our class
properties as `public var width` and so on if we want mutable contents. On the
whole, Temper favors immutable data, but mutable is still available.

Feel free to exit the REPL using ++ctrl+d++ and restart it at any time to
clean up the state. But you also can redefine over old names if you want, such
as by using a full `let` or `class` declaration.


## Class methods and top-level functions

It's not fun to call something a class that only has data storage, so let's
redefine our `Rectangle` type and add methods to it (see [issue#22](https://github.com/temperlang/temper/issues/22) on
automating `.toString()`):

```temper
class Rectangle(
  public width: Float64,
  public height: Float64,
) {
  public area(): Float64 {
    width * height
  }

  // Using `get` allows later "getter" usage without `()` syntax, even if
  // inconsistency between `area` and `perimeter` is likely unwise.
  public get perimeter(): Float64 {
    2.0 * (width + height)
  }

  public scaled(by: Float64): Rectangle {
    // If multiple `Rectangle` definitions exist in the REPL, being explicit
    // here with the type name can help for now.
    { class: Rectangle, width: by * width, height: by * height }
  }

  public toString(): String {
    // String interpolation automatically calls `.toString()` on given values.
    "Rectangle of size ${width} x ${height}"
  }
}
```

Now let's call some methods:

```temper
$ let box = { width: 1.5, height: 0.5 };
interactive#1: void
$ box.area()
interactive#2: 0.75
$ box.perimeter // no parens because getter
interactive#3: 4.0
$ box.scaled(3.0)
interactive#4: {class: Rectangle__0, width: 4.5, height: 1.5}
$ "Here's a ${box.scaled(2.0)}"
interactive#5: "Here's a Rectangle of size 3.0 x 1.0"
```

Temper also has top-level functions. Let's make one:

```temper
// Function definitions use `let` just like variables.
let areaPerPerimeter(rectangle: Rectangle): Float64 {
  // Destructuring works on any getter.
  let { perimeter } = rectangle;
  rectangle.area() / perimeter
}
```

And we can try it out:

```temper
$ areaPerPerimeter(box)
interactive#7: 0.1875
$ areaPerPerimeter({ width: 1.0, height: 1.0 })
interactive#8: 0.25
```


## Backend translation

All the evaluation above is done in Temper's own interpreter, but the REPL also
lets us sneak a peek into backend translation. Let's say our definition for
`areaPerPerimeter` above was input `#6` with this output:

```temper
$ let areaPerPerimeter(rectangle: Rectangle): Float64 {
    // Destructuring works on any getter as well as simple properties.
    let { perimeter } = rectangle;
    rectangle.area() / perimeter
  }
interactive#6: void
```

We can use that to get translations into different backends. For example:

```temper
$ translate(6, "csharp")
Translated csharp for interactive#6 ...
$ translate(6, "java")
Translated java for interactive#6 ...
$ translate(6, "js")
Translated js for interactive#6 ...
$ translate(6, "lua")
Translated lua for interactive#6 ...
$ translate(6, "py")
Translated py for interactive#6 ...
```

The translations are rather messy, and the REPL does its best guess of what to
include for context. Focus for now on just the definition of `areaPerPerimeter`
for each of those backends. The translations look something like this:

=== "C\#"

    ```cs
    public static double AreaPerPerimeter(Rectangle rectangle__3)
    {
        Rectangle t___0 = rectangle__3;
        double perimeter__5 = t___0.Perimeter;
        return rectangle__3.Area() / perimeter__5;
    }
    ```

=== "Java"

    ```java
    public static double areaPerPerimeter(Rectangle rectangle__3) {
        Rectangle t_0 = rectangle__3;
        double perimeter__5 = t_0.getPerimeter();
        return rectangle__3.area() / perimeter__5;
    }
    ```

=== "JS"

    ```js
    /**
     * @param {Rectangle_5} rectangle_2
     * @returns {number}
     */
    export function areaPerPerimeter(rectangle_2) {
      const t_3 = rectangle_2;
      const perimeter_4 = t_3.perimeter;
      return rectangle_2.area() / perimeter_4;
    };
    ```

=== "Lua"

    ```lua
    areaPerPerimeter = function(rectangle__3)
      local t_0, perimeter__5;
      t_0 = rectangle__3;
      perimeter__5 = t_0.perimeter;
      return temper.fdiv(rectangle__3:area(), perimeter__5);
    end
    ```

=== "Python"

    ```py
    def area_per_perimeter(rectangle__3: 'Any0') -> 'float1':
      t_0: 'Any0' = rectangle__3
      perimeter__5: 'float1' = t_0.perimeter
      return rectangle__3.area() / perimeter__5
    ```

We can clean up REPL translation output further in the future (and see also
[issue#34](https://github.com/temperlang/temper/issues/34) about the `'Any0'` type), but the `translate` helper function is
already useful to get an idea of what kinds of translations Temper makes today.
Going back to the [getting started tutorial](./index.md), you can also look at
the output of `temper build` for each backend when you're building Temper source
files as a library.


## More on translation

That Python function heading above says
`def area_per_perimeter(rectangle__3: 'Any0') -> 'float1'`. The name
`area_per_perimeter` properly uses snake case for Python, but what's with that
`rectangle__3` parameter name? It looks hard to use as a named argument. That's
true. Temper defaults to using certain hints about the decisions it makes, which
includes guessing when a parameter should be made friendly for named use on a
backend. Specifics might change in future releases of Temper.

We'll get more detail on this case later. We also intend to provide more
capabilities to Temper over time to customize how backend translation makes its
decisions.

For further exploration, try translating the class definition itself and compare
the translations of `area()` and `get perimeter()` or the properties `width` and
`height`.

When you're done exploring here, let's move on to `interface` definitions.


## Links
- **NEXT**: [Interfaces](02-interface.md)
- **PREVIOUS**: [Getting started](./index.md)
- Reference: [Builtins](../reference/builtins.md),
  [Types](../reference/types.md)
