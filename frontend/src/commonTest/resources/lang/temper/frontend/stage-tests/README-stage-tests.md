# Stage tests

Here's an example of a layout of files for use with the
*assertModuleAtStage* test harness that powers most of our
testing of frontend staging.

```sh
frontend/src/commonTest/kotlin/lang/temper/frontend/stage-tests/
  README-stage-test.md  # This file
  syntaxMacro/my-test-id/
    README.md # ignored by test harness.  Comments for maintainer
    work/    # The work root
      test/
        README.md   # Ignored
        test.temper   # The source for the main module to process
      # Any other temper or temper.md files that the
      # module under test might need to import.
    expect/
      README.md               # Ignored
      disAmbiguate.temper     # stage output as temper pseudocode
      disAmbiguate.lispy      # Lispy output
      disAmbiguate-types.json # Information about declared types
      disAmbiguate-meta.json  # JSON snapshot of module metadata
      disAmbiguate-appendix.json # JSON snapshot of module metadata
      # similarly file groups for other stages
      errors.json             # Expected error messages
      stdout.txt              # Expected console output from any run stage
      stage-completed.txt     # Max stage completed
      run-result.json         # Expected result from the run stage
```

Any files starting with `README` and ending with `.md` are ignored
as if they don't exist.  So are emacs droppings like `*~` files.

A test directory is usable with `StageTestDir("...")`.
A test directory must have subdirectories `work/` and `expect/`.

The files in the `expect/` subdirectory specify how much staging is
done, and how to compose the JSON-looking bundle that shows up in
JUnit diffs when an *assertModuleAtStage* test fails.

# Small files under `expect/`

Here's a listing of the small files under `expect` that are combined to make the diff bundle:

For each *Stage* element (*Import*, *DisAmbiguate*, *SyntaxMacro*, *Define*, *Type*, *FunctionMacro*, *Export*, *GenerateCode*) you can have files with any or all or none of these extensions:
  - `*.lispy` specifies the detailed `Tree.toLispy()` dump of the AST at that stage
  - `*.temper` specifies the `Tree.toPseudoCode()` dump of the AST at that stage
  - `.meta.json` specifies Module metadata snapshot including metadata about exports and declared types.

For the runtime emulation stage, *Stage.Run*, there are a few files:

- `run.txt` is the concatenation of all `console.log` inputs with line breaks added at runtime.
- `run.result.json` is the JSON dump of the module result.

`errors.json` is required if the log sink includes entries with severity >= *Log.Warn* that are not filtered out by options passed to *assertModuleAtStage*.
