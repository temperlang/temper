### Name selection and "temper.keep/"

Each Temper backend has to handle name style conversion and also choosing names
to address conflict with backend language rules. For example, *switch* is a
keyword in some languages but not others. Name choices also might vary from
build to build.

To stabilize name choices and provide user customization over name selection, we
have begun saving name choices at build time. Some items of note:

- Names are saved in directory `temper.keep/` as a placeholder for
  build-generated files that should be kept in source control in the future.
- However, the data stored here is currently in preview, so it should be ignored
  until some future release.
- The `temper.keep/` name, as well as `temper.out/`, might change its name or
  structure in future releases.
- Temper doesn't yet load or use saved name choices at this time.

In other words, this is an experiment toward features in future releases.
