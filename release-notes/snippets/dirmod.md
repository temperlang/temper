### Directory modules

Temper now supports importing full directories as modules rather than individual
files, and this is also now the recommended way for structuring libraries. All
files in a directory module share the same module namespace.

If `config.temper.md` has no imports, it implicitly imports the current
directory as a top-level module for the library:

```temper
    // If this is all you want in config, no need. It's implied by default.
    // Also, note no trailing "/" on imported dirs.
    import(".");
```
