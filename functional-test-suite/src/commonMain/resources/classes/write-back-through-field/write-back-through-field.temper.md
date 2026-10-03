# Write Back Through Field Functional Test

A method on `Ping` calls a method on one of its own fields, and that
method writes back to the same `Ping` before returning. The write has to
see the object free to change, and the caller has to see the new value.

    class Pong {
      public pong(a: Ping): Void { a.n += 1; }
      public bump(a: Ping, by: Int): Int { a.n += by; a.n }
    }

    class Ping(public peer: Pong) {
      public var n: Int = 0;
      public var most: Int = 0;
      public ping(): Int { peer.pong(this); n }
      // `n` is read before the call and passed in, then changed by the call.
      public pass(): Int { peer.bump(this, n) }
      // Two fields read in one expression, with a write in between.
      public record(): Boolean {
        n += 1;
        if (n > most) { most = n; }
        n > most - 1
      }
      // A field read on each side of a call that changes it.
      public around(): Int { n + peer.bump(this, 10) + n }
    }

    let p = new Ping(new Pong());
    console.log("ping=${ p.ping() }");
    console.log("pass=${ p.pass() }");
    console.log("record=${ p.record() } most=${ p.most }");
    console.log("around=${ p.around() } n=${ p.n }");

Expected output:

```log
ping=1
pass=2
record=true most=3
around=29 n=13
```
