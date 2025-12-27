# Private Method Functional Test

Tests invoking a private method from within a public method.

    class C {
        private helper(x: Int): Int { x + 1 }
        public method1(x: Int): Int { helper(x * 2) }
        public method2(x: Int): Int { helper(x * 3) }
    }

    let c = new C();

    console.log("method1(10)=${ c.method1(10) }");
    console.log("method2(10)=${ c.method2(10) }");

Expected output:

```log
method1(10)=21
method2(10)=31
```
