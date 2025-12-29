# Package lang.temper.compile

<!-- The h1 name is specially interpreted by dokka -->

Compiler machinery that handles birthing modules, shepherds them
through the frontend and connects the results, when ready, to
backends.

## High level design of the Temper compiler

TODO: Talk about:

- file tree snapshotting and hashing
- watch service
- building and running requests against backends
  - varieties of requests
- how dependency info is used to do partial rebuilds
- library roots, config files, and how source files are grouped into modules
- file layout under temper.out
- file layout under temper.keep
- relationship to tooling, cli, and langserver

## Local sources vs remote sources

A local Temper source file is one whose generated sources will be part
of the output artifacts.

Any other Temper source files' outputs will be treated as dependencies.

TODO: What about parameterizations of modules that are not mentioned in that
module's local source set?

