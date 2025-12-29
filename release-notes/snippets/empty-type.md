### *Empty* type

The *Void* type may not bind to type parameters making it awkward to use
with generic functions.

Many functional languages have a type, often called *Unit*, that is
subtly different.  Instead of meaning "no need for space on the stack
for the result," it means "the result allows for no further operations."

*Empty* is such a type.  It should translate to *Unit* on typed,
functional language backends.

*Void* returning functions often translate the same as *null*
returning functions in dynamic language backends, but *empty()*

Here's the problem with *Void*: the block's return type, *Void*
cannot bind to *&lt;T&gt;* in *Fn (): T*.

```temper inert
let callThenLog<T>(f: fn (): T): T {
  let result = f();
  console.log("I called f!");
  result
}
callThenLog { (): Void;; // ERROR: Void cannot bind to T
  console.log("Someone called me!");
}
```

With *Empty* instead, this works.

```temper
let callThenLog<T>(f: fn (): T): T {
  let result = f();
  console.log("I called f!");
  result
}
callThenLog { (): Empty;; // OK
  console.log("Someone called me!");
  empty()
}
//!outputs "Someone called me!"
//!outputs "I called f!"
```
