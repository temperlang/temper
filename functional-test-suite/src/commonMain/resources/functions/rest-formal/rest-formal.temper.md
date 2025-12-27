# Rest Formal Functional Test

Tests that a rest argument is passed through as a list.

    let foo(...bar: List<String>): Void {
      const length: Int = bar.length;
      console.log(length.toString());
    }

    foo("1");
    foo("1", "2");
    foo();
    console.log("done");

Expected output:

```log
1
2
0
done
```
