# Java statics

Public statics as Java users see them: each is read through a public
static getter (`getStandard()`, `isStrict()` for a `Boolean`, and
`getLimit()` for a literal too), and its field is private, or the
package's where the module's class sets it, so host code can't
reassign it. `standard` and `strict` read module state, so the
module's initializer sets them; making `standard`'s `Policy` runs
`Policy`'s static initializer before the value exists, as Kelpto's
`SanitizerPolicy.standard` does, so a `final` field couldn't hold it.
`tests/host/Statics.java` uses them from another package, loading
`Policy` before the module.

    let allowed = "a b";

    export class Policy(public names: String) {
      public static standard: Policy = new Policy(allowed);
      public static strict: Boolean = allowed.isEmpty;
      public static textOnly: Policy = new Policy("");
      public static limit = 3;
      public static label = "policy";
      public static on = true;
      private static hidden: Policy = new Policy("x");
      public static peek(): String { Policy.hidden.names }
    }

    export let describe(policy: Policy = Policy.standard): String {
      "[${policy.names}]"
    }

    console.log("${describe()} ${Policy.strict.toString()}");
    console.log("${Policy.textOnly.names}|${Policy.limit.toString()}|${Policy.label}|${Policy.on.toString()}|${Policy.peek()}");

```log
[a b] false
|3|policy|true|x
```
