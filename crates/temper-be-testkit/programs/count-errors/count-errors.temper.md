# Count errors

A library with an error root of its own, which `host-declared-errors` imports
to catch its errors beside its own in one handler. Alone, it logs
nothing.

    export sealed interface CountError extends Error {}
    export class BadCount(public text: String) extends CountError {}

    export let parseCount(text: String): Int throws BadCount {
      when (text) {
        "zero" -> 0;
        "two" -> 2;
        else -> throw new BadCount(text);
      }
    }
