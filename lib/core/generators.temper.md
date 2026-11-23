# Generators and promises

Generators, which compute results step by step, and promises, placeholders
for values that may not be known yet, as `async` and `await` use them.

## GeneratorResult

*GeneratorResult* is one of a sequence of results from a [snippet/type/Generator].

    @partialImu
    @connected
    export sealed interface GeneratorResult<@out YIELD extends AnyValue> {}

## Generator

*Generator* is a multi-step computation that may be scheduled for a step by calling the
[snippet/type/Generator/method/next] method.

Block lambdas that extend [snippet/type/GeneratorFn] specify factories
for *Generator*s, or if they don't [snippet/type/Bubble], for
[snippet/type/Generator]s.

Terminology note:
Coroutines may be represented as *Generator*s where
[snippet/type/Generator/method/next]
starts or resumes computation and the coroutine pauses itself
when done with the step.
We avoid the term "coroutine" because many programmers associate it
narrowly with a kind of parallelism.

    @connected
    export interface Generator<@out YIELD extends AnyValue> {
      /**
       * Starts/resumes the computation and runs until it pauses or completes.
       *
       * If the computation was [snippet/type/Generator/property/done] prior
       * to the start of the call or it is done at the end, returns [snippet/type/DoneResult].
       * This is the case for a [snippet/type/GeneratorFn] that
       * [returns][snippet/builtin/return].
       *
       * If the computation pauses, returns the value associated with the
       * decision to pause.
       * For a [snippet/type/GeneratorFn], this is the value passed to
       * [snippet/builtin/yield].  If a *GeneratorFn* bubbles, the call to
       * next propagates it.
       */
      @connected
      public next(): GeneratorResult<YIELD> throws Bubble;
      /**
       * True if subsequent calls to [snippet/type/Generator/method/next]
       * would do no work and return a [snippet/type/DoneResult].
       *
       * That "if" is not an "if and only if" because in the case where a
       * [snippet/type/GeneratorFn] body yielded just before its return,
       * *done* may be [snippet/builtin/false] but a subsequent call to
       * *next* would return a *DoneResult* and reading *done* after that
       * would get [snippet/builtin/true].
       */
      @connected
      public done: Boolean;
      /**
       * Should be called once it is realized that
       * [snippet/type/Generator/method/next] is not going to be called
       * again to release resources needed by *next*.
       *
       * After *close* exits, [snippet/type/Generator/property/done]
       * will be [snippet/builtin/true].
       */
      @connected
      public close(): Void;
    }

## SafeGenerator

*SafeGenerator* is a variant of [snippet/type/Generator] that does not bubble
when computing the next result.

    export interface SafeGenerator<@out YIELD extends AnyValue> extends Generator<YIELD> {
      /**
       * Starts/resumes the computation and runs until it pauses or completes.
       *
       * If the computation was [snippet/type/Generator/property/done] prior
       * to the start of the call or it is done at the end, returns [snippet/type/DoneResult].
       * This is the case for a [snippet/type/GeneratorFn] that
       * [returns][snippet/builtin/return].
       *
       * If the computation pauses, returns the value associated with the
       * decision to pause.
       * For a [snippet/type/GeneratorFn], this is the value passed to
       * [snippet/builtin/yield].
       */
      @connected
      public next(): GeneratorResult<YIELD> throws Bubble {
        nextSafe()
      }

      @connected
      public nextSafe(): GeneratorResult<YIELD>;
    }

## ValueResult

*ValueResult* is a result produced from [snippet/type/Generator/method/next] when there is a value to produce and
[snippet/type/Generator/property/done] is [snippet/builtin/false].

    @partialImu
    @connected
    export class ValueResult<@out YIELD extends AnyValue> extends GeneratorResult<YIELD> {
      public value: YIELD;
      @connected
      public constructor(value: YIELD): Void {
        this.value = value;
      }
    }

## DoneResult

*DoneResult* is a result produced from [snippet/type/Generator/method/next] when there is no value to produce and
[snippet/type/Generator/property/done] is [snippet/builtin/true].

    @imu
    @connected
    export class DoneResult<@out YIELD extends AnyValue> extends GeneratorResult<YIELD> {
      public constructor();
    }

`doneResult` gives a [snippet/type/DoneResult], a generator's result once it is done.

    @connected
    export let doneResult<YIELD extends AnyValue>(): DoneResult<YIELD> { new DoneResult<YIELD>() }

## GeneratorFnWrapper

*GeneratorFnWrapper* wraps a [snippet/type/GeneratorFn] as a [snippet/type/Generator].

    class GeneratorFnWrapper<@out YIELD extends AnyValue>
    extends Generator<YIELD> {
      private let generatorFn: fn (): GeneratorResult<YIELD> throws Bubble;
      private var _done: Boolean = false;

      public constructor(generatorFn: fn (): GeneratorResult<YIELD> throws Bubble) {
        this.generatorFn = generatorFn;
      }

      public next(): GeneratorResult<YIELD> throws Bubble {
        if (this._done) {
          return doneResult<YIELD>();
        }
        this._done = true; // GeneratorFns are not re-entrant.
        let result: GeneratorResult<YIELD> = generatorFn();
        when (result) {
          is ValueResult<YIELD> -> do { this._done = false }
          is DoneResult<YIELD> -> void
        }
        return result;
      }

      public get done(): Boolean { this._done }

      public close(): Void {
        // TODO: Stop a paused generatorFn, as by bubbling at its pause point.
        // For now, closing does nothing.
      }
    }

## SafeGeneratorFnWrapper

*SafeGeneratorFnWrapper* is like *GeneratorFnWrapper* but wraps as a [snippet/type/SafeGenerator].

    class SafeGeneratorFnWrapper<@out YIELD extends AnyValue>
    extends SafeGenerator<YIELD> {
      private let generatorFn: fn (): GeneratorResult<YIELD>;
      private var _done: Boolean = false;

      public constructor(generatorFn: fn (): GeneratorResult<YIELD>) {
        this.generatorFn = generatorFn;
      }

      public nextSafe(): GeneratorResult<YIELD> {
        if (this._done) {
          return doneResult<YIELD>();
        }
        this._done = true; // GeneratorFns are not re-entrant.
        let result: GeneratorResult<YIELD> = generatorFn();
        when (result) {
          is ValueResult<YIELD> -> do { this._done = false }
          is DoneResult<YIELD> -> void
        }
        return result;
      }

      public get done(): Boolean { this._done }

      public close(): Void {
        // TODO: Stop a paused generatorFn, as by bubbling at its pause point.
        // For now, closing does nothing.
      }
    }

## Promise

*Promise* is a placeholder for a value that may not be known yet.

This class provides no methods that introspect over whether that
value is available or what it is.
Use [builtin/await] to get at the value asynchronously.

    @connected
    export class Promise<@out R extends AnyValue> {
      private constructor();
    }

## PromiseBuilder

*PromiseBuilder* contains a promise that may be shared, but the
builder itself may be withheld.

Security note: in some target languages, there is no distinction between
promises and promise builders.  In those languages, native code that
receives a promise builder's promise might be able to preempt completion
meaning that third-parties that await on promise get the preemptively
provided resolution instead of that provided by the owner of the promise
builder.

    @connected
    export class PromiseBuilder<R extends AnyValue> {
      @connected
      public get promise(): Promise<R>;
      @connected
      public constructor(): Void;

      @connected
      public complete(completion: R): Void;

      @connected
      public breakPromise(): Void;
    }
