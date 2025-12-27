# Constness functional test

Tests:

    do {
      // non-const with initializer
      var nci = 1;
      console.log("nci = ${nci}");
      (nci = 2) orelse console.log("failed to set nci");
      console.log("nci = ${nci}");

      // non-const without initializer
      var nc;
      nc = 1;
      console.log("nc = ${nc}");
      (nc = 2) orelse console.log("failed to set nc");
      console.log("nc = ${nc}");

      // non-const declared inside a loop
      for (var i = 0; i < 3; i += 1) {
        var ncl: Int;
        (ncl = i) orelse console.log("failed to set ncl");
        console.log("ncl = ${ncl}");
      }

      // const with initializer
      let ci = 1;
      console.log("ci = ${ci}");
      // (ci = 2) orelse  // TODO: statically reject.  MagicSecurityDust not required to check.
        console.log("failed to set ci");
      console.log("ci = ${ci}");

      // const without initializer
      let c: Int;
      c = 1;
      console.log("c = ${c}");
      // (c = 2) orelse   // TODO: statically reject.  MagicSecurityDust not required to check.
        console.log("failed to set c");
      console.log("c = ${c}");

      // const declared inside a loop
      for (var i = 0; i < 3; i += 1) {
        let cl;
        (cl = i) orelse console.log("failed to set cl");
        console.log("cl = ${cl}");
      }
    }

Expected output:

```log
nci = 1
nci = 2
nc = 1
nc = 2
ncl = 0
ncl = 1
ncl = 2
ci = 1
failed to set ci
ci = 1
c = 1
failed to set c
c = 1
cl = 0
cl = 1
cl = 2
```
