# Nullable edges

What's particular to Rust's `Option`s: a nullable string field that's
`var`, with its setter; `Float64?` and `Int64?` compared; `as` from a
nullable value; an `orelse` whose body has statements, also as a
function's last value; an `orelse` whose fallback bubbles and whose
body takes lines, held in a local that a parameter's name mustn't
clash with; and a nullable class value narrowed and called.
The log is what the interpreter gives.

    export class Label(public var text: String?) {
      public shown(): String {
        let t = text;
        if (t != null) { t } else { "(none)" }
      }
    }

    let sameFloat(a: Float64?, b: Float64?): Boolean { a == b }
    let sameLong(a: Int64?, b: Int64?): Boolean { a == b }

    let must(s: String?): String throws Bubble { s as String }

    let longer(s: String?): Int {
      let v: Int = do {
        let t = must(s);
        let n = t.toInt32();
        n + 1
      } orelse 0;
      v
    }

    let tried(s: String): Int {
      do {
        console.log("trying ${s}");
        s.toInt32()
      } orelse -1
    }

    interface Part {}
    class Code(public text: String) extends Part {}
    class NoCode() extends Part {}

    let firstCode(attempt: Part): Int throws Bubble {
      (attempt as Code).text[String.begin] orelse do {
        console.log("no code");
        bubble()
      }
    }

    let label = new Label(null);
    console.log(label.shown());
    label.text = "hi";
    console.log(label.shown());
    console.log("${sameFloat(1.5, 1.5)} ${sameFloat(null, 0.0)} ${sameFloat(null, null)}");
    console.log("${sameLong(2i64, 2i64)} ${sameLong(2i64, null)}");
    console.log("${longer("41")} ${longer(null)} ${longer("x")}");
    console.log(must("ok") orelse "bad");
    console.log("${tried("7")} ${tried("x")}");
    console.log("${firstCode(new Code("a")) orelse -1} ${firstCode(new NoCode()) orelse -1}");

```log
(none)
hi
true false true
true false
42 0 0
ok
trying 7
trying x
7 -1
no code
97 -1
```
