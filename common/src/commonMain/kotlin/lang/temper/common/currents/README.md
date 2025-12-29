# Package lang.temper.common.currents

<!-- The h1 name is specially interpreted by dokka -->

Abstractions related to concurrency including *CancelGroup* which tracks
futures so that a command line tool or LSP instance can kick off a build
and cancel all the futures and child-processes related to it before
starting a new build when it detects changes to sources.

*CancelGrup* is a central clearinghouse for concurrency.  It allows:

- Creating cancellable futures
- Delayed tasks

*RFuture* augments the JVM's future machinery with extra error handling.
An *RFuture* is like a result in that there are well-typed success and
failure paths, but there is a third path by which it can complete:
unexpected throwable which is not type bounded except by *Throwable*.

It does this via thin wrappers around the JVM's finely tuned
`java.util.concurrent` machinery.

----

Instead of taking callbacks for concurrent actions, file handling
and other naturally async APIs can provide Kotlin/Common futures.

It also adds support for some patterns.  *Currents* chokepoints
exceptions so instead of trying and catching, code can do

    future.await { (result, failure, throwable) ->
      // One of those three names will be non-null.
    }

This makes type-checked failure handling the norm.

----

Futures by themselves are susceptible to race conditions.

    val x = returnsAFuture()
    // <====
    val finalResult = x.then {
      ...
    }

If the future completes at the arrow, any count of outstanding futures
might reach zero, before a future gets created as a result of the
`.then` chain.

By creating a future in the context of a cancel group, we can avoid a
scenario where cancellation happens between starting chaining and
execution of the chained block.
