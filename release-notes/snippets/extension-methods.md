### extension functions allow extensions to method call syntax

Previously `subject.verb(object)` syntax worked only when `subject`'s
type declared a method named `verb`.

Now, regular functions can be declared with the `@extension` decorator
and participate in the same syntax.

```temper
class C {
  public isExtension(): Boolean { false }
}

@extension("isExtension")
let otherIsExtension(
    s: String // The first argument is the subject
): Boolean { true }

!(new C().isExtension()) && // Regular method call
"Hello".isExtension()       // Invoking a regular function with dot syntax
```

Similarly, `@staticExtension` allows extending
`SubjectType.verb(object)` syntax.  The `@staticExtension` decorator
takes a subject type and the verb text.

```temper
@staticExtension(Int, "three")
let intThree(): Int { 3 }

Int.three() == 3
```

See the documentation for `@extension` and `@staticExtension`
for more details.
