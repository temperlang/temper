# Break and Continue from loops

## Basic Usage

Without a label `break;` and `continue;` will break the most-inner loop.

### Continue

Continue skips the rest of a loop iteration.

    for (var i = 0; i < 5; i++) {
      if (i == 1 || i == 3) {
        continue;
      }
      console.log(i.toString());
    }

```log
0
2
4
```

### Break

Break stops the loop entirely.

    for (var i = 0; i < 5; i++) {
      if (i == 3) {
        break;
      }
      console.log(i.toString());
    }

```log
0
1
2
```

## Labeled Usage

Labels can be used with `continue` like `continue outer;` does in this example.

## Continue

    var n = 0;
    outer: for (var i = 0; i < 4; i++) {
      var str = "row ${i} =";
      var j = 0;
      while (true) {
        str = "${str} ${n++}";
        if (i <= j) {
          console.log(str);
          continue outer;
        }
        j += 1;
      }
      console.log(str);
    }

```log
row 0 = 0
row 1 = 1 2
row 2 = 3 4 5
row 3 = 6 7 8 9
```

### Primes with Continue

This Example is a silly prime number generator for `n < 49` using various `continue`.

    var prime = 1;
    loop1: for (var i = 0; i < 25; i++) {
      loop2: for (var j = 0; j < 25; j++) {
        loop3: for (var k = 0; k < 25; k++) {
          prime += 1;
          if (prime != 2 && prime % 2 == 0) {
            continue loop1;
          }
          if (prime != 3 && prime % 3 == 0) {
            continue loop2;
          }
          if (prime != 5 && prime % 5 == 0) {
            continue loop3;
          }
          if (prime != 7 && prime % 7 == 0) {
            continue;
          }
          console.log(prime.toString());
        }
      }
    }

```log
2
3
5
7
11
13
17
19
23
29
31
37
41
43
47
```

## Break

Labels can also be used with `break` like `break outer;` does in this example.

    var val = 0;
    outer: for (var i = 0; i < 10; i++) {
      var str = "row ${i} =";
      var j = 0;
      while (true) {
        str = "${str} ${val++}";
        if (i == 4) {
          console.log("break");
          break outer;
        }
        if (i <= j) {
          console.log(str);
          continue outer;
        }
        j += 1;
      }
      console.log(str);
    }

```log
row 0 = 0
row 1 = 1 2
row 2 = 3 4 5
row 3 = 6 7 8 9
break
```
