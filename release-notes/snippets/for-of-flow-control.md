### `break` and `continue` work in `for (… of …)` loop bodies

`for (let x of subject) { body }` is syntactic sugar for
`subject.forEach { (x);; body }`.

Normally, `break` and `continue` statements have to appear directly
within the same function as the loop.
Since `.forEach` is a method, it's body is defined on `subject`'s type.

Now, `.forEach` method implementations can opt into inlining.
When they do not require any `private` implementation details of the
type, their bodies can be inlined allowing `break` and `continue` to
appear to Temper backends as part of the same function body.

Previously the below would have been an error, but now it logs the
even numbers from the list.

```ts
for (let i of [0, 1, 2, 3, 4, 5, 6]) {
  if ((i & 1) == 1) {
    continue
  }
  console.log(i.toString())
}
```
