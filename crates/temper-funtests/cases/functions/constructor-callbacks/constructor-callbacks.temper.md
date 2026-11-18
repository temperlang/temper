## Constructors and functions with callback blocks

Check that function references get handled correctly by backends. Start with a
class and also a factory function.

    export class Hi<T>(public ha: fn (T): T) {}
    export let makeHi<T>(ha: fn (T): T): Hi<T> { new Hi(ha) }

Now make functions with local closures that call either of the above. The
version with direct constructor `new` call was failing in Java, and the one
calling the factory was failing in C\#. As in, they weren't even compiling.

    export let newIntHi(j: Int): Hi<Int> { new Hi(fn (i: Int): Int { i + j }) }
    export let makeIntHi(j: Int): Hi<Int> { makeHi { (i: Int): Int => i + j } }

One key point is just that the above exports compile on backends.

It would also be nice to use things from above, but we seem to have frontend
problems, so disable usage tests for now.

```temper inert
    console.log(newIntHi(1).ha(2).toString())
    console.log(makeIntHi(4).ha(5).toString())
```

If we enable the calls above, instead of this output:

```ignore log
3
9
```

We get this error:

```txt
14: ): Hi<Int> { makeHi { (i: Int): Int => i
                        ⇧
[work//constructor-callbacks.temper.md:14+51]@D: Never reached by macro expander (S)

Failed before runtime
```
