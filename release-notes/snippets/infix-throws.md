### 🚨Deprecated ` | Bubble` syntax in favour of infix `throws`

Previously, a function definition communicated that it could fail
by including ` | Bubble` at the end of its return type.

Other recent changes, to express nullable types as `T?` instead of
`T | Null` did away with reasons for `|` as a type operator.

Now using `|` as a type operator issues a warning.

```sh
$ temper repl
$ let f(): Int | Bubble { 0 }
1: let f(): Int | Bubble { 0 }
            ┗━━━━━━━━━━┛
[interactive#0:1+9-21]@D: `|` is deprecated for types. Prefer `T?` to `T | Null` and `T throws F` to `T | Bubble`
interactive#0: void
```

The preferred syntax is now to use `throws` instead of `|`:

```sh
$ let g(): Int throws Bubble { 0 }
interactive#1: void
$ g()
interactive#2: 0
```
