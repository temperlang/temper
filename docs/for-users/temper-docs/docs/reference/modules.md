---
title: Modules & Files
---

# Modules & Files

## File name extensions and Markdown embedding

Temper source files are identified by their file name extension. For
now, these are:

- `.temper`
- `.temper.md`

In the former case, that's a plain Temper source file. In the latter
case, it's Temper embedded in a Markdown document, enabling
(semi-)literate programmming. We might add other official file name
extensions or document languages to embed Temper within in the future.

In Temper Markdown, the file starts out in comment mode. That is, the
text of the document is a comment to the Temper compiler. Temper code
goes inside code blocks:

````md
# My Temper Markdown source file

This is text. You can treat it as a document.

```temper
// This is active Temper code.
fn greet(name: String): Void {
  console.log("Hello, ${name}!);
}
```

Within `.temper.md` files, the default code block type is `temper`,
so you can leave off the label:

```
// Still Temper.
greet("world");
```

Or even use indented blocks:

    // And still Temper.
    greet("everyone");

If you want a block of Temper for documentation purposes but which you
don't want to be active, you can call it `inert`:

```temper inert
console.log("Inert code doesn't run.");
```
````

All Temper code blocks start and end on new lines. We might also
require blocks to begin and end at statement boundaries.

Also, we plan to specify how specific class and function documentation
can be extracted from Temper Markdown in the future.

## Parts of a Temper source file

In Temper, [`import`](builtins.md#builtin-import), works on *module*s, not files or
directories. Normally, there is a 1:1 relationship. Each *directory* (or
folder) specifies one module, but that is not always the case. All
Temper source files in a module directory are merged into a single
module, irrespective of file names, except for library configuration
files named "config.temper.md". Each such config file specifies the root
of a library.

Further, a *module preface* allows you to specify definitions shared by
all instances of the module's body. All preface content from all Temper
source files in a module are merged together.

If a preface specifies no *module parameters* then there will be
exactly one module instance, corresponding to the empty parameter set.

If it specifies parameters, then there may be zero or more
instances.

``` mermaid
flowchart LR
  subgraph File [Temper File]
  X[["// preface\n;;;\n// body"]]
  end
  subgraph Modules
  Preface[["// preface\n;;;"]]
  ModuleA[["// body"]]
  ModuleB[["// body"]]
  Preface --> ModuleA
  Preface --> ModuleB
  end
  File ---> Modules
```

The instances of a module are determined by the parameters
passed when `import`ing that module.
There will be one instance for each distinct parameterization
so if multiple modules import with the same parameters, they
will share an instance.

The parts of a Temper source file are separated by
triple-semicolon `;;;` tokens.  Since this is token-based,
`;;;` character sequences inside a comment or string literal
do not affect the structure of the file.

- If there are no `;;;` tokens, then the source file has an
  empty preface, and all tokens are in the body.
- If there is one `;;;` token, then it separates the
  preface before from the body after.
- If there are two `;;;` tokens, then the tokens after the
  second must be well-formed JSON, and may be used by tools
  to store metadata about the file.
- If there are three or more `;;;` tokens, the file is malformed.

|           |       |           |       |            |       |           |
| --------- | ----- | --------- | ----- | ---------- | ----- | --------- |
| *Body*    |       |           |       |            |       |           |
| *Preface* | `;;;` | *Body*    |       |            |       |           |
| *Preface* | `;;;` | *Body*    | `;;;` | *Metadata* |       |           |
| *Invalid* | `;;;` | *Invalid* | `;;;` | *Invalid*  | `;;;` | *Invalid* |
