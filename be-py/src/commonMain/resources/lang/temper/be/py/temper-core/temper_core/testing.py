"""
A very simple test runner to grab what we need and format as xml, so we don't
have to install anything extra for testing.
"""

import dataclasses as dc
import datetime as dt
import unittest as ut
import sys
import xml.etree.ElementTree as et
import time as t
import typing as typ


@dc.dataclass
class Failure:
    kind: str  # "error" | "failure"
    message: str
    text: str


@dc.dataclass
class TestCaseResult:
    name: str
    time: float
    failure: typ.Optional[Failure] = None


class SuiteResult(ut.TestResult):
    def __init__(self):
        super().__init__()
        self.error_count = 0
        self.failure_count = 0
        self.message = ""
        self.start_time = 0.0
        self.tests = typ.cast(typ.List[TestCaseResult], [])

    def addError(self, test, err):
        super().addError(test, err)
        self.error_count += 1
        # Potentially large message that's redundant with full text.
        self.message = ""

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.failure_count += 1
        # Technically possible to provide no message, causing args len 0.
        self.message = (err[1].args[0:1] or [""])[0]

    def startTest(self, test):
        super().startTest(test)
        self.start_time = t.time()

    def stopTest(self, test: ut.TestCase):
        super().stopTest(test)
        # Presume up to one of either error or failure.
        failure = None
        if self.errors:
            failure = self._pop_failure("error", self.errors)
        elif self.failures:
            failure = self._pop_failure("failure", self.failures)
        self.tests.append(
            TestCaseResult(
                # We feed raw test name into docstring which feeds short desc.
                name=test.shortDescription() or test.id(),
                time=t.time() - self.start_time,
                failure=failure,
            )
        )
        pass

    def _pop_failure(self, kind, failures):
        return Failure(kind=kind, message=self.message, text=failures.pop()[1])


def main():
    # Run tests.
    suite = ut.defaultTestLoader.discover("tests", top_level_dir=".")
    suite_result = SuiteResult()
    start_time = t.time()
    suite.run(suite_result)
    full_time = t.time() - start_time
    # Convert to xml, but only the parts we currently parse.
    root = et.Element("testsuites")
    suite_node = et.SubElement(
        root,
        "testsuite",
        name="suite",
        timestamp=dt.datetime.now().isoformat(timespec="seconds"),
        time=f"{full_time:.3f}",
        tests=str(suite_result.testsRun),
        errors=str(suite_result.error_count),
        failures=str(suite_result.failure_count),
    )
    for test_result in suite_result.tests:
        test_node = et.SubElement(
            suite_node,
            "testcase",
            name=test_result.name,
            time=f"{test_result.time:.3f}",
            classname=test_result.name,
        )
        failure = test_result.failure
        if failure is not None:
            failure_node = et.SubElement(
                test_node,
                failure.kind,
                message=failure.message,
            )
            failure_node.text = failure.text
    # Write out.
    et.indent(root)
    doc = et.ElementTree(root)
    out_name = sys.argv[1:2]
    if out_name:
        with open(out_name[0], "wb") as out_file:
            doc.write(out_file, encoding="utf-8")
    else:
        doc.write(sys.stdout, encoding="unicode")


if __name__ == "__main__":
    main()
