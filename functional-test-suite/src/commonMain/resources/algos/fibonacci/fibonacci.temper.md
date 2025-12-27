# Fibonacci Functional Test

A straightforward implementation of the fibonacci series.

    let fib(var i: Int): Int {
      var a: Int = 0;
      var b: Int = 1;
      while (i > 0) {
        let c = a + b;
        a = b;
        b = c;
        i -= 1
      }
      a
    };
    var zero = 0;
    zero = zero;
    console.log("fib(1)=${ fib(zero + 1) }");
    console.log("fib(10)=${ fib(zero + 10) }");
    console.log("fib(20)=${ fib(zero + 20) }");

Expected output:

```log
fib(1)=1
fib(10)=55
fib(20)=6765
```
