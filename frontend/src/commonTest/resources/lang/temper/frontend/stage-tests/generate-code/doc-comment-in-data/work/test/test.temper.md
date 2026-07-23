    /** Is this a doc comment? */
    export let hi = List.of<Int>(
      1,

Here is some text, don't you know.

      2,
      /** How about this? */
      3,
    );
    export let f(/** docs */ a: Int): Int { g(/** here too? */ 1) }
    let g(b: Int): Int { b }
