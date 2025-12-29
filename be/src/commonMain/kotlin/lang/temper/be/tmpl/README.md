# Package lang.temper.be.tmpl

Machinery to produce a layered Temper-like AST from a fully processed
Temper module set.

This serves to bridge the backend, with a relatively small number of
high frequency contributors with backends maintained by (hopefully) a
larger number of occasional contributors.

The lispy form is flexible and allows for ease of meta-operations like
"find and transform all x" and is explicitly an expression language.
It intentionally allows multiple ways of representing concepts like
`if` as the code moves through the translation/staging pipeline.

This intermediate form will have a rigid layering:
- module sets contain modules
- modules contain top levels
- top levels contain declarations and statements
- statements contain expressions
It has one way of representing `if` which serves only to represent `if`.

The latter is straightforward to translate into an output language AST
with some of those layering requirements.
