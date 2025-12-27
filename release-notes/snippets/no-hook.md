### Removed hook operator: `(condition ? consequent : alternate)` ⚠️

The C-style hook operator, also known as the ternary operator, has been removed from Temper.

Previously, `condition ? consequent : alternate` was equivalent to

    if (condition) {
      consequent
    } else {
      alternate
    }

It had been deprecated in documentation.
Now, it is unrecognized syntax.  Since `if` statements can appear as expressions, the
hook operator was unnecessary.
