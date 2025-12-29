Make some would-be cycles here, because we define `Person` in "things" and use
it in "defs", ...

    export class Person(
      public let name: String,
      public let age: Int,
    ) {}

... but then we also define `third` in "defs" then use it in "things".

    export let twoThirds = twice(third);

All of this should be automatically handled.
