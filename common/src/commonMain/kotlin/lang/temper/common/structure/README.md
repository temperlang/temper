# Package lang.temper.common.structure

<!-- The h1 name is specially interpreted by dokka -->

## Simpler testing with *Structure Reconciliation*

I often find myself writing code that creates **complex** objects so that I can compare them to **complex** objects created by the code I'm testing.  If I do everything right though, my tests run <font color=green>green</font>, but if not I have to figure out how this **complex** value differs from that **complex** value.  Debugging quickly gets ... **complex**.

What <span id="goals">I'd like</span> is a way to check a complex value that:
- doesn't require writing complex test code
- doesn't require assuming that **complex** constructors and `.equals` methods function correctly
- make it easy to see what **differs** in a failing test
- let me write tests for **small** values that test a **lot of details**
- let me test **big** values with **less detail**

I'm going to explain how an approach called *structure reconciliation* can make all of this a lot easier.

Common IDEs and unit test frameworks have really good tools for displaying the differences between two strings, so I'm building this:

![](https://i.imgur.com/iZKZqeF.png)


Given a string describing the *structure* of the complex value, and the *computed* value from the code under test, we get a visual difference.

The first question we need to answer is "what is *Structure*?"  Well, I decided, rather arbitrarily, that our test input is going to be JSON for no other reason than that everyone knows it so somebody reading the tests for the first time will get the gist.

So for our purposes, we discover a values structure by converting it to JSON.  A *Structured* value is one that knows how to convert itself to JSON; maybe by sending messages to a *StructureSink* describing itself.

```kotlin=
interface Structured {
  fun destructure(sink: StructureSink)
}
```

A *StructureSink* needs to get enough information to build a string of JSON.  We don't want structured objects to be building strings of JSON though; properly formatting strings is **complex** and we don't want to make the code we're testing more **complex**.

```kotlin=
interface StructureSink {
  // JSON includes strings, numbers, booleans
  fun value(s: String)
  fun value(n: Double)
  fun value(n: Long)
  fun value(b: Boolean)
  // A special value, `null`
  fun nil()
  // So now a StructuredValue can call
  // these methods on a sink to tell it
  // about the values of properties that
  // comprise it.

  // It'd be nice to use the same
  // value(...) syntax for complex values.
  fun value(s: Structured?) = if (s == null) {
    nil()
  } else {
    s.destructure(this)
  }

  // JSON also has two kinds of bundles:
  // Arrays and Objects.

  // An Array has a bunch of values in order.
  fun arr(
     elements: (StructureSink).() -> Unit
  )
  // This means to make an array, you pass
  // a closure that enumerates the values
  // in the array.

  // An Object is like an array but it contains
  // "properties" instead of values.
  fun obj(
     properties: (PropertySink).() -> Unit
  )
}
// where a PropertySink lets you associate
// a key and a value.
interface PropertySink {
  fun key(name: String, hints: Set<StructureHint> = emptySet(), valueSink: (StructureSink).() -> Unit)
  // Don't worry about what hints are for now.
}
```

Now, a complex type can expose its structure.  Imagine we have a *Book* type.

```kotlin=
class Book {
  private val isbn: String
  private val title: String
  private val author: Person
  private val yearPublished: Int
}
```

That type can expose its structure thus:

```kotlin=
class Book : Structured {
  ...

  override fun destructure(sink: StructureSink) = sink.obj {
    key("isbn") { value(isbn) }
    key("title") { value(title) }
    key("author") { value(author) }
    // Works if author is Structured
    key("yearOfPublished") { value(yearPublished) }
  }
}
```

That can be a tad repetitive so, for convenience, subclassing a type that uses reflection gets you the equivalent.

```kotlin=
class Book : StructuredViaReflection
```

This divison of duty between
- *Structured* which knows describes itself, and
- *StructureSink* which processes a stream of structure messages

lets us move the cloud back a bit.

![](https://i.imgur.com/S05dt7e.png)

A *JSON Parser* can feed events to a *StructureSink*, and a *StructureSink* implementation can buld a data tree.  On the right side, a different *StructureSink* implementation can generate a string of properly-indented, canonical JSON so we can feed two JSON strings to `diff`.

So now we can compare apples to apples; err trees to trees.  If we do nothing else, we can get a nice diff when something goes wrong.

```diff
 {
     "isbn": "ISBN-01234",
-    "title": "A Very Important Book",
+    "title": "A Very Important\u00a0Book",
     "author": {
       ...
     },
     "yearPublished": 2000
 }
```

But let's revisit our [goals](#goals):
- ~~let me write tests for **small** values that test a **lot of details**~~
- let me test **big** values with **less detail**

"Less detail" is kind of a fuzzy concept.  Less detail means we can ignore non-essential details, but our tests would be less maintainable if we could accidentally ignore essential details.

Maybe an example would help.  Imagine we're testing a URL type.  We might dump a URL value's structure to JSON and get:

```json=
{
    "protocol": "https",
    "authority": {
      "user": null,
      "password": null,
      "host": "example.com",
      "port": "null"
    },
    "path": "/",
    "query": null,
    "fragment": null,
    "href": "https://example.com/"
}
```

That's a mouthful!  Notice a few things:
- Some fields in complex types are almost never used.  For example `.authority.password`.  A test-case author shouldn't have to specify these ad nauseam, nor should a maintainer have to read them.
- Some fields are redundant.  `.href` packs a lot of information in but is derivable from the other fields.  When testing the URL library, you'd probably want to test that those are in sync.  But when testing a client of that library you might not.

The guiding principle of structure reconciliation is this:
- Values know how to describe themselves.
- Test authors know what they want to test.

*Structure reconciliation* then is the process of deriving a *structured* value with the
- **same content** as the **computed value**
- **same shape** as the **test case**

That *reconciled output* is then diffed against the *expected output* to produce a nice, human-readable diff.

![](https://i.imgur.com/63nl1vw.png)

Remember above when I said to ignore *StructureHint*s for now in:

```kotlin=
// where a PropertySink lets you associate
// a key and a value.
interface PropertySink {
  fun key(name: String, hints: Set<StructureHint> = emptySet(), valueSink: (StructureSink).() -> Unit)
  // Don't worry about what hints are for now.
}
```

To let values specify what's "necessary", they can provide hints.

| Hint | Meaning |
| ---- | ------- |
| *Sufficient* | the property's value could substitute for the whole object.  For example, the URL's `href` property could; it contains all the information. |
| *Unnecessary* | the property can be skipped. |
| *NaturallyOrdered* | human's think about the property's value as appearing in a particular position. |

Some examples might help understand how these work together.

```kotlin=
@NaturalOrder([
    "year", "month", "day"
    // Because I'm not a monster (I kid (I don't))
])
data class SimpleDate(
    val year: Int,
    val month: Int,
    val day: Int
) : StructuredViaReflection {
    @StructuredProperty([Sufficient, Unnecessary])
    val isoString get() =
        "${pad(year, 4)}-${pad(month, 3)}-${pad(day, 3)}"
}
```

Here's a date type with `.year`, `.month`, and `.day` fields, and a computed `.isoString` property.

The first rule of structure reconciliation is

### A *Sufficient* property's value is substitutable for the whole.

- Expected: `"2020-04-26"`
- Computed: `SimpleDate(2020, 4, 27)`
- Tree: `{ "year": 2020, "month": 4, "day": 27, "isoString": "2020-4-27" }`
- Reconciled: `"2020-04-27"`
- Diff:
  ```diff
  -"2020-04-26"
  +"2020-04-27"
  ```

Remember, the *reconciled* value has the same content as the value being tested, but the same shape as the expected test result.

The second rule of structure reconciliation is

### An *Unnecessary* property that doesn't appear in the expected result can be ignored.

- Expected: `{ "year": 2020, "month": 5 }`
- Computed: `SimpleDate(2020, 4, 27)`
- Tree: `{ "year": 2020, "month": 4, "day": 27, "isoString": "2020-04-27" }`
- Reconciled: `{ "year": 2020, "month": 4, "day": 27 }`
- Diff:
  ```diff
   {
       "year": 2020,
  -    "month": 5
  +    "month": 4,
  +    "day": 27
   }
  ```

The `isoString` property is *Unnecessary* so doesn't appear in the reconciled output, but the `day` property does because it's necessary.

The last rule of structure reconciliation is

### An object whose necessary properties are *NaturallyOrdered* is equivalent to an array with those properties' values.

- Expected: `[ 2020, 5, 27 ]`
- Computed: `SimpleDate(2020, 4, 27)`
- Tree: `{ "year": 2020, "month": 4, "day": 27, "isoString": "2020-04-27" }`
- Reconciled: `[ 2020, 4, 27 ]`
- Diff:
  ```diff
   [
       2020,
  -    5,
  +    4,
       27
   ]
  ```

## Limitations

This assumes that the object graphs under test are acyclic.  Cyclic
object graphs are not representable as JSON, and as long as cycle
breaking is arbitrary, may not be as amenable to visual diffing.

*StructureAdapter* helps `mySink.value(ext)` do something useful
when *x*'s type is not under your control so does not subtype
*Structured*.  *StructureAdapter* may be slow.
