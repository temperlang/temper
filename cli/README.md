# Temper command line interface

The `temper` cli is the public entry point for Temper tooling, including the:
- interactive REPL/console
- library build process
- language server
- test runner

This subproject includes a thin cli interface over functionality elsewhere in
the Temper codebase. Gradle build tasks here also build binaries in support of
standalone Temper release artifacts.

## Standalone Temper

Currently, we have tasks in place to support two forms of binary distributions
of Temper:

- As a bundle of jar files, runner scripts, and a jlinked JRE.
  - These inherit Java's slow startup.
  - They interface more easily with external Java code.
- As a single native executable produced by GraalVM `native-image`.
  - This is smaller than the tarball bundle.
  - It starts much faster.
  - It interfaces less easily with external Java code.
  - It might run core Temper compilation more slowly than an ordinary JRE.
  - It currently fails to `build` libraries correctly. This might relate to
    other concurrency issues in BuildConductor.

## Building the Temper-JRE bundle

- If you change the jlink configuration in `linkJre`, run gradlew `bundleJre`
  before `composeTemperJre` or other things depending on it.
- Also update [jre bundles in our jr4temper repo](jre4temper) to make sure they
  get used in our CI actions.

## Building the GraalVM native image

The GraalVM Native Image plugin for Gradle depends on having a GraalVM JDK
detectable by Gradle. This might mean a formal install, or alternatively, you
can manually place an extract GraalVM tree under Gradle's `jdks` directory,
[marked as provisioned][gradle-jdk].

## Configuring for Graal native image creation

The `native-image` tool needs information about reflection, native interfacing,
and so on. In the `cli` subproject, this configuration is found, per GraalVM
recommendation, under:

```
src/main/resources/META-INF/native-image
```

These files were created by both automated and manual effort:

- Automated by running `temper` under GraalVM's JDK with the additional flag:
  ```
  -agentlib:native-image-agent=config-merge-dir=...path-to-out-dir...
  ```
  - For now, this is based on manually running `temper build`, `temper repl`,
    and also `temper serve` with manual exercise of language server features.
    We need to automate the proper capture process in the future.
  - Running in-process unit tests with this flag is ineffective because the
    test runner and the tests themselves produce substantially more
    configuration than core Temper.
- Manually adding addition configurations based on failures attempting to
  perform tasks with the produced executable `temper` binary.

[gradle-jdk]: https://discuss.gradle.org/t/gradle-does-not-detect-java-toolchains-on-windows/39908/5
[jre4temper]: https://github.com/temperlang/jre4temper/releases
