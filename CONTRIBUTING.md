# Contributing to Temper

Thank you for your interest in contributing to Temper! This guide will help you get set up for development.

## Prerequisites

Not all contributions are code.  We appreciate any kinds of contributions: documentation, visualizations, ideas, all of it.

That said, if you do want to run the test suite or rebuild the doc site yourself, you will need:

- **JDK 17** — Required by Gradle, the build system
- **Gradle** — [Installation guide][gradle installation]
- **Git** with [commit signing] set up

If you get stuck, see [*Questions*](#questions) below.

### For running the full test suite

- **Node.js v18** — For JavaScript backend tests; `nvm install lts-hydrogen`
- **Python 3.11** — For Python backend tests
- **Maven** (>3.2.5) — For Java tests; run `scripts/generate-maven-toolchains-xml` to configure
- **.NET Core 6.0** — For C# backend tests
- **Rust 1.63+** — Including cargo, for Rust backend tests

## Getting Started

```sh
# Clone the repository
git clone git@github.com:temperlang/temper.git
cd temper

# Install pre-commit hooks
pip install pipx
pipx install poetry
poetry -C scripts install
./scr init-workspace
```

## Building and Testing

We recommend using the Gradle wrapper:

```sh
# Run all tests
./gradlew check

# Run tests for a specific subproject
./gradlew common:check

# Run faster subset of tests (skips slow Kotlin/Linux compile)
./gradlew fast

# Format Kotlin code (run before committing)
./gradlew ktlintFormat
```

The first run downloads dependencies and may take a few minutes.

## Updating Generated Code

```sh
# Format Kotlin source files
./gradlew ktlintFormat

# Update generated code from .grammar files
./gradlew kcodegen:updateGeneratedCode

# Update generated docs from Temper inline-docs
./gradlew build-user-docs:updateGeneratedDocs
```

## IDE Setup (IntelliJ)

1. Install the **Kotlin** and **PsiViewer** plugins
2. Import the project: `File > New > Project from Existing Sources`
3. Select the repository directory and choose "Import project from external model" → Gradle
4. Add `project/dictionary.dic` as a custom dictionary in `Settings > Editor > Natural Languages > Spelling`

### Optional: Color output in terminal

Add the environment variable `IS_INTELLIJ_TERMINAL=t` to your run configurations for colored test output.

## Project Structure

| Directory | Purpose |
|-----------|---------|
| `common/` | Utilities not specific to Temper |
| `lexer/` | Tokenization |
| `parser/` | Token stream → CST |
| `ast/` | CST → AST transformation |
| `frontend/` | Module processing |
| `be/` | Backend machinery |
| `be-js/`, `be-py/`, etc. | Language-specific backends |
| `cli/` | Command-line interface |
| `langserver/` | IDE integration (LSP) |
| `functional-test-suite/` | Cross-backend test cases |

## Windows Tips

- If building in both WSL and Windows in the same directory causes errors, run `./gradlew clean`
- The pre-commit hook works with Git for Windows (includes bash)

## Building Documentation

```sh
# Serve user docs locally
./gradlew build-user-docs:serveUserDocs

# Build Kotlin API docs
./gradlew dokkaHtmlMultiModule
# Output: build/dokka/htmlMultiModule/index.html
```

## Preparing a pull request

The Temper project manages changes via the standard Github pull request process.

We need two things for a commit to *main*:

- [commit signing], cryptographically signing commits, lets us produce builds with supply chain security metadata that is [required by some large organizations](https://engineering.homeoffice.gov.uk/standards/signing-code-commits/).
- [commit signoff], `git commit`'s `-s` flag, is different and ensures that the people who authored and authorized changes for inclusion in the main branch are identified in the record so all the transfers under [Github's inbound=outbound][inbound=outbound] rules, and other terms of use, are clear.

## Questions?

- [Discord](https://discord.gg/QQKgZMukVB) — Chat with the community
- [Issues](https://github.com/temperlang/temper/issues) — Report bugs or request features

[commit signing]: https://docs.github.com/en/authentication/managing-commit-signature-verification/about-commit-signature-verification
[commit signoff]: https://git-scm.com/docs/git-commit#Documentation/git-commit.txt--s
[gradle installation]: https://docs.gradle.org/current/userguide/installation.html#installing_with_a_package_manager
[inbound=outbound]: https://docs.github.com/en/site-policy/github-terms/github-terms-of-service#6-contributions-under-repository-license
