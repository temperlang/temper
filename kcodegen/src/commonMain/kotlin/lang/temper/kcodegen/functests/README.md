# Functional Test Suite Supporting Files Generator

The templates correspond to specific files:

* [ConfigTemperTemplate]
  * `functional-test-suite/src/commonMain/resources/config.temper.md`
* [FunctionalTestsTemplate]
  * `functional-test-suite/src/commonMain/kotlin/lang/temper/tests/FunctionalTests.kt`
* [FunctionalTestSuiteITemplate]
  * `functional-test-suite/src/commonMain/kotlin/lang/temper/tests/FunctionalTestSuiteI.kt`

These try to be reasonably consistent with the structure of the original files, and are
generally some HEADER, entry function and FOOTER.

[FunctionalTestSuitener] itself is a standard code generator. It scans for markdown-based
functional tests within `functional-test-suite/src/commonMain/resources` to produce
those files.
