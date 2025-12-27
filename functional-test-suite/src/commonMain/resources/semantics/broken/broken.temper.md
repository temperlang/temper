# Broken Functional Test

Just an easy way to push bad Temper code through to backends to ensure they
handle things gracefully rather than crashing or whatnot.

These things should and do have frontend errors, but we purposely push to
backends, anyway.

```text
@meta:arg allowedErrors = setOf(
@meta:arg     "BecauseNameUndeclared",
@meta:arg     MessageTemplate.IllegalAssignment.name,
@meta:arg     MessageTemplate.SignatureInputMismatch.name,
@meta:arg     MessageTemplate.CannotTranslate.name,
@meta:arg ),
@meta:arg expectRunFailure = true,
```

## Side Note

If this call goes here instead of later, it turns some of the functions into
predeclarations and assignments, which then breaks new naming assignment in
be-java.

    // doesntExist();

## Use Undefined Names in Function

Different from the case above, because it crashes in TmpLTranslator before it
ever gets to backends.

    let undefinedPlusArgInside(): Int { missing + 1 }

## Partially Finished Function

Fail to return a value in every case. Was crashing be-csharp.

    let padLeft(string: String, minSize: Int, pad: String): String {
      let needed = minSize - string.countBetween(String.begin, string.end);
      if (needed <= 0) {
        return string;
      }
      // Should continue here.
    }

## Export Without Let

Tempting to do this when defining methods like `public whatever()` works, but
this doesn't work today. Was crashing be-csharp, be-java, and be-lua.

    export something() {}

## Call Undefined Function

Was crashing be-csharp and be-java.

    doesntExist();
