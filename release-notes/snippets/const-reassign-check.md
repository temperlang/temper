### Reassignment of `const` declarations are now reported as errors.

`let` declarations are only allowed to be assigned once.
`var` declarations are allowed to be reassigned.

That former restriction was not fully enforced but now it is.

```temper
let f(n: Int): String {
  let i = n;   // Temper does not require `const` keyword.
  ++i;         // Reassignment
  return i.toString();
}
console.log(f(41)); //!logs "42"
```

Now, running that produces an error message:

```
3: ++i;         // Reassig
   ┗━┛
[interactive#2:3+2-5]@T: i__4 is reassigned after interactive#2:2+10-11 but is not declared `var` at interactive#2:2+2-11
2: let i = n;   // Temper does n
           ⇧
2: let i = n;   // Temper does n
   ┗━━━━━━━┛
```
