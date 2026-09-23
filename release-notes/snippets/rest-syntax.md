### 🚨Breaking change: Rest parameter syntax removed.

Previously, Temper allowed variadic functions, functions
that can accept more arguments than the number of declared
formals, when the extra arguments could fit into a
homogeneous list that is bound to a "rest" parameter.

```temper inert
export let sum(
  ...nums: Int32  // ERROR: syntax not supported
): Int32 {
  var total = 0;
  for (let num of nums) {
    total += num;
  }
  total
}
```

These were hard to translate. Dynamic languages tend to
handle them fine, but TmpL had two special forms:
an expression for the count of rest arguments, and
an expression for the n-th rest argument.
Most backends didn't bother translating them, so
it was unclear that translation was generally possible.

But internally, the way rest parameters were represented
in function signatures complicated a lot of things.

It was also unclear how to support connected, variadic
functions.

Temper may reintroduce rest parameters in the future,
in a way that only puts a burden on backends that want
to allow rest-calling conventions, and in a way that
does not have extra moving parts for the vast majority
of function signatures that are non-resty.
(Perhaps by establishing the convention that if the last
parameter has a type like `RestList<T>` then extra
parameters can be auto grouped into a list, so rest
parameter calling is a compiler fiction for list passing.)

Some builtins are still variadic: the list constructor
and string concatenation.  But variadicity is not supported
in user code.
