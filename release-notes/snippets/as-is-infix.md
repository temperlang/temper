### 🚨Breaking change: Infix `as` and `is` operators

Previously, casting and runtime type checks looked like this in Temper:

```temper inert
let something = a.as<Something>();
if (another.is<Something>()) {
    // do other things
}
```

Now, they look like this:

```temper inert
let something = a as Something;
if (another is Something) {
    // do other things
}
```

Note that infix `as` also still means rename in destructuring contexts:

```temper inert
let { exportedName as localName } = import("...");
let { a as b } = c();
```
