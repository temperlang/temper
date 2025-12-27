    export let first = "a first thing";
    export let second = { name: "Second", age: 2 };
    export let third = 0.333;
    export let fourth = doThings(5, "hi") { (a: Int, b: String): String =>
      "${b} ${a}"
    }
    export let fifth = makeFifth("!");

Here make a call back that uses a local var:

    export let makeFifth(suffix: String): String {
      doThings(5, "hi") { (a: Int, b: String): String =>
        "${b} ${a}${suffix}"
      }
    }
