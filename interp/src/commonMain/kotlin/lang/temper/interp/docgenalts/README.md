# Package lang.temper.interp.docgenalts

<!-- The h1 name is specially interpreted by dokka -->

Alternate builtins used in the documentation pipeline.

See [Genre.Documentation][lang.temper.lexer.Genre.Documentation] for
background on the documentation pipeline.

Documentation only allows a limited set of macros, and requires that
control flow like `if` appear only in statement position.

These alternate builtins check these constraints, and allow us to
shuttle `if` and friends through the frontend pipeline to the TmpL
translator without needing the *Weaver* stage which could introduce
temporary variables that look ugly in translated documentation.
