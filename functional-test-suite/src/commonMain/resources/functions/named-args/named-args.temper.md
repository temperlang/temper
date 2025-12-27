# Named Args Test

This functional test was designed to explore named args, which we now don't
have, except for object literal property bags. But there were some interesting
things here, so we adapted it to the new world.

## Simple defaults and named args

Default args should work for instance methods as well as top level functions,
but named values only work for constructing class instances.

So make an abusive class that has a constructor only for side effects, so we
can see what happens.

    class Greet {
      public constructor(
        @noProperty message: String = "Hi!",
        @noProperty repeat: Boolean = false,
      ): Void {
        console.log(message);
        if (repeat) {
          console.log(message);
        }
      }
    }
    new Greet();
    new Greet("Bye!");

```log
Hi!
Bye!
```

Now for named args. Check first that they work if in order and none are skipped.

    { message: "Hola!", repeat: true };
    { message: "Salut!" };

```log
Hola!
Hola!
Salut!
```

## Skipped and out-of-order

Skipped and out-of-order also work. We can also skip positionally with `null`.

    new Greet(null, true);
    { repeat: true };
    { repeat: true, message: "Ni hao!" };

```log
Hi!
Hi!
Hi!
Hi!
Ni hao!
Ni hao!
```

Also check side effects for execution order.

    var message = "Hi!";
    var repeat = false;
    let changeMessage(): Boolean {
      message = "Namaste!";
      repeat
    }
    let changeRepeat(): String {
      repeat = true;
      message
    }
    { repeat: changeMessage(), message: changeRepeat() };

They should be evaluated in actual argument order, *not* formal parameter order.

```log
Namaste!
```

## Default constructors with default and named args

Classes get default constructors by default, and these should recognized default
and named args.

For now, test only in order.

    class Person(
      public name: String = "Alice",
      public age: Int = 40,
    ) {
      public toString(): String {
        "{ class: Person, name: ${name}, age: ${age} }"
      }
      public greet(greeting: String = "Hi"): Void {
        console.log("${greeting}, ${name}");
      }
    }
    console.log(new Person().toString());
    console.log(new Person("Bob").toString());
    console.log("${{ name: "Carrie", age: 50 }}");

Named args only work for construction, not for other methods.

    { name: "Dan" }.greet("Hallo");

```log
{ class: Person, name: Alice, age: 40 }
{ class: Person, name: Bob, age: 40 }
{ class: Person, name: Carrie, age: 50 }
Hallo, Dan
```

## Trailing callback

We also allow trailing blocks after optionals.

    class CalcOptions(
      public scale: Int = 1,
      public origin: Int = 0,
    ) {}

    let calc<T>(
      n: Int,
      transform: fn (Int): T,
      options: CalcOptions = { class: CalcOptions },
    ): T {
      let { origin, scale } = options;
      transform((n - origin) * scale)
    }
    console.log(calc(2) { (n): String => n.toString() });
    console.log(calc(2, { scale: 3, origin: 4 }) { (n): String =>
      n.toString()
    });

```log
2
-6
```

Also demonstrate skipped-by-name properties with trailing block. This was more
interesting with named args, but retaining the example allows showing how
classes still allow expandable named options.

    console.log(calc(2, { scale: 3 }) { (n): String => n.toString() });
    console.log(calc(2, { origin: 4 }) { (n): String => n.toString() });

```log
6
-2
```

We can move the logging call into the block by using the Empty type.

    calc(2, { scale: -2 }) { (n): Empty =>
      console.log(n.toString());
      empty()
    }

```log
-4
```
