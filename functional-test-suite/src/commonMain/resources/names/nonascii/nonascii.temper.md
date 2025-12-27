# Non-ASCII names

Test that non-ASCII names translate to something sensible.

Some backends might have to mangle them horribly, so this test might
incorporate a "suggested ASCII ID" annotation in the future.

But backends that have basic support for [UAX #31] should manage
as-is.

    /** Uses a non-ASCII input parameter */
    let sayHello(
      /** όνομα is a Greek noun that translates to "name" in English. */
      όνομα: String
    ): Void {
      console.log("Hello, ${όνομα}!");
    }

And we can run a simple "Hello, World!" test.

    sayHello("World");

```log
Hello, World!
```

[UAX #31]: https://www.unicode.org/reports/tr31/
