---
title: Target Languages
---

# Target Languages

`temper build` translates to many *target* programming languages via
*Backend*s ([source][temper/be/**/Backend.kt]) that each embed knowledge
about one target language.  Each backend is responsible for:

- converting Temper syntax trees to *target language* syntax trees, and
- creating source files and source maps under the `temper.out` directory, and
- connecting to language specific tools to

    - post-process the source files, and
    - run the translated tests and collect results, and
    - bundle them into publishable archives.

The *Backend IDs* noted below may be used with the `-b` flag
(aka `--backend`) to the `temper` command line tool to
build, run, or test using specific backends.
See `temper help` for more information on `-b`.

## Target Language List
⎀ backends/supported -heading
