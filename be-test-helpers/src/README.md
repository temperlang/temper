# Module be-test-helpers

<!-- The h1 name is specially interpreted by dokka -->

Common test code for output-language specific backends which
are in the `be-*` subprojects where the `*` is an abbreviation
for the output language.

The test code is under *src/commonMain* so it can be depended
upon by those other projects' *src/commonTest* source groups.
