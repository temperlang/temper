### 🚨Breaking change: visibility notations required on class members

Previously, if a class definition member was missing a notation, the
member defaulted to `private`.

```temper
class C {
  f(): Int { 42 }
  public g(): Int { f() }
}
```

Now, all class members require a visibility notation: one of `public`,
`private`, or `protected`.

```diff
 class C {
-  f(): Int { 42 }
+  private f(): Int { 42 }
   public g(): Int { f() }
 }
```
