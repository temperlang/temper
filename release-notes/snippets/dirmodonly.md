### Modules named using directory paths

Release 0.2.0 introduced directory modules. This is now the only way
to define modules which affects existing import statements, output
file layout, and diagnostic messages

Now, `temper build` creates a module for each directory that contains
one or more `*.temper` or `*.temper.md` files.  That module is named
based on the directory only both within the compiler and in the
metadata provided to backend translators.

#### Diagnostic methods change

This affects module names in compiler messages.
Previously you might have seen:

    [my-lib//path/dir/file.temper]: Error message guff

Now, you'll see:

    [my-lib//path/dir/]: Error message guff

Line numbered code snippets still include file paths.

#### Backend file paths change

Backends have changed how they allocate output file names.  Now, a
Temper module with a single Temper source file does not have that
file's base-name as part of the output file path.

#### Updating existing code

Most Temper code should continue to compile without changes since
directory modules were enabled for `temper build`.  There are a
few breaking changes.

Previously, `import("std/regex.temper.md")` worked.  You could
import via a specifier string with a Temper extension on it.
That no longer works.  Below is an example of the kind of changes needed:

```diff
 let { StringBuilder } = import(
-  "std/strings.temper.md"
+  "std/strings"
 );
```

Valid module identifiers are now either relative paths starting with one of (`.`, `..`) or a fully-qualified specifier as below:

1. A library name, like `std`
2. Zero or more `/`s followed by directory names specifying
   the relative path of the directory within the library.
3. No trailing slash.

The "no trailing slash" caveat applies to relative paths too.
