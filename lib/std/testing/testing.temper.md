# Temper test framework

## Test instance

We currently convert assert and check macro calls into method calls on a `Test`
instance.

    export class Test {

Recommended default Temper assert is soft, meaning that it records failures for
reporting but doesn't immediately end test execution on a false value. This lets
you check multiple conditions more easily.

      @connected
      public assert(success: Boolean, message: fn (): String): Void {
        if (!success) {
          _passing = false;
          _messages.add(message());
        }
      }

A test can also fail outright: `fail` records its message and ends the test
there, as a failure. Nothing in Temper can stop it, no `orelse` in the test or
in the code under test, so only the test runner sees it. Since `fail` never
returns, `if (n == null) { test.fail("no n") }` narrows `n` after the `if`.

      @connected
      public fail(message: String): Never<Void> {
        _passing = false;
        _messages.add(message);
        _failedOnAssert = true;
        bail()
      }

Typical hard asserts that end the test on false condition also are available,
failing as `fail` does.

      @connected
      public assertHard(
        success: Boolean,
        message: fn (): String,
      ): Void {
        if (!success) {
          fail(message());
        }
      }

To check that something panics, as reading past a list's end does, put it in
a block: `test.assertPanics { ls[5]; }` passes if the block panics. If the
block returns, the test fails, saying "expected a panic", or the message given
before the block, as in `test.assertPanics("past the end") { ls[5]; }` (a
trailing block goes to the last function parameter, so the message can be
last among the parameters, and left out). An error is no panic, so if the
block throws one, the test fails too, saying so. Like `assert`, a failing
`assertPanics` lets the test go on.

Only a panic in the block is caught, never one before or after it. A panic
here is any failure but an error or a test's failure, on every backend, so a
host's own error counts too, and a `test.fail` in the block, or a host test
framework's failed assertion, still ends the test (on Rust, a host's
`assert!` is a panic). This is for tests alone: what
the block changed before it panicked stays changed, maybe half done.

      @connected
      public assertPanics(
        block: fn (): Void throws Error,
        message: String = "expected a panic",
      ): Void {
        var threw = false;
        let panicked = panics { block() orelse do { threw = true } };
        // The block ended the test, by `fail` or a hard assertion, which some
        // backends end the test by panicking.
        if (_failedOnAssert) { bail() }
        if (!panicked) {
          _passing = false;
          _messages.add(if (threw) { "${message}, but it threw" } else { message });
        }
      }

Run a block, giving whether it panicked: whether it failed by anything but an
error or a test's failure, which pass through, except where a test failure
is a panic, as `bail` ends a test by panicking on some.

      @connected
      private panics(block: fn (): Void): Boolean;

Harden and end current test on any pending failure if not previously hardened.
Backends typically insert calls to this if needed, but you can also call it
manually at any desired point in your test.

      @connected
      public softFailToHard(): Void {
        if (hasUnhandledFail) {
          _failedOnAssert = true;
          bail();
        }
      }

End the test with its failures. Backends customize how: each throws, or
panics, with the test's messages, as their test frameworks report failures,
in a way no `orelse` catches. The interpreter reports the messages as the
test's failure.

      @connected
      private bail(): Never<Void>;

You can check the current passing state of the test at any time. A test is
currently passing if all soft checks and hard asserts have been successful.

TODO Does this need to be function call syntax for macro purposes?

      @connected
      public get passing(): Boolean { _passing }

Messages access is presented as a function because it likely allocates. Also,
messages might be automatically constructed in some cases, so it's possibly
unwise to depend on their exact formatting.

      @connected
      public messages(): List<String> { _messages.toList() }

### Backend helper methods

Avoid using backend helper methods in user code. Their behavior might be
unreliable on some backends and/or have high risk of changing in future releases
of Temper.

      @connected
      public get failedOnAssert(): Boolean { _failedOnAssert }

Additional helper methods to simplify backend code generation in some contexts.

      public get hasUnhandledFail(): Boolean { !(_failedOnAssert || _passing) }

Simple helper to get multiple messages combined for now. We probably want to do
fancier things in the future, but this can simplify backends for now.

      public messagesCombined(): String? {
        if (_messages.isEmpty) {
          // Unexpected, but most backends can do something with null.
          null
        } else {
          _messages.join(", ") { it => it }
        }
      }

      private var _failedOnAssert: Boolean = false;
      private var _passing: Boolean = true;
      private _messages: ListBuilder<String> = new ListBuilder<String>();
    }

## Interpreter testing support

NOTICE: Don't directly use anything in this section. It just exists for the
implementation of testing within the interpreter.

A test may throw any error, which fails it, saying the error's message after
any of its own. Only tests, which the compiler makes, and std may declare
`throws Error`.

    export let TestCase = Pair<TestName, TestFun>;
    export let TestFailureMessage = String;
    export let TestFun = fn (Test): Void throws Error;
    export let TestName = String;
    export let TestResult = Pair<TestName, List<TestFailureMessage>>;

    @connected
    export let processTestCases(testCases: List<TestCase>): List<TestResult> {
      testCases.map { (testCase): TestResult =>
        let { key, value as fun } = testCase;
        let test = new Test();
        // Actually call the test, then run the async work it started, so
        // what fails there fails it.
        let thrown: String? = do { fun(test); null } catch (e) { e.message };
        finishAsyncWork();
        // Now get the messages.
        let messages = test.messages();
        let failures: List<TestFailureMessage> = if (test.passing && thrown == null) {
          []
        } else if (thrown != null && !test.failedOnAssert) {
          // The test threw an error, apart from its asserts, so add that on.
          let allMessages = messages.toListBuilder();
          allMessages.add(thrown);
          allMessages.toList()
        } else {
          messages
        };
        // Package up with test name.
        new TestResult(key, failures)
      }
    }

    @connected
    export let reportTestResults(
      testResults: List<TestResult>,
      writeLine: fn (String): Void,
    ): Void {
      // Write as junit xml for consistency with our other backends.
      // TODO Inject some call to gather this info in structured form?
      writeLine("<testsuites>");
      let total = testResults.length.toString();
      let fails = testResults.reduceFrom(0) { (fails: Int, testResult): Int =>
        fails + (if (testResult.value.isEmpty) { 0 } else { 1 })
      }.toString();
      let totals = "tests='${total}' failures='${fails}'";
      // Just lie about time for now since it's required.
      writeLine("  <testsuite name='suite' ${totals} time='0.0'>");
      for (var i = 0; i < testResults.length; i += 1) {
        let testResult = testResults[i];
        let failureMessages = testResult.value;
        let name = escapeXml(testResult.key);
        let basics = "name='${name}' classname='${name}' time='0.0'";
        if (failureMessages.isEmpty) {
          writeLine("    <testcase ${basics} />");
        } else {
          writeLine("    <testcase ${basics}>");
          let message = escapeXml(failureMessages.join(", ") { it => it });
          writeLine("      <failure message='${message}' />")
          writeLine("    </testcase>");
        }
      }
      writeLine("  </testsuite>");
      writeLine("</testsuites>");
    }

    @connected
    export let runTestCases(testCases: List<TestCase>): String {
      let report = new StringBuilder();
      reportTestResults(processTestCases(testCases)) { line =>
        report.append(line);
        report.append("\n");
      }
      report.toString()
    }

A test ends only once the async work it started has run as far as it can:
no block is ready to run and no I/O is in flight. So a soft `assert` in an
`async` block counts, and what ends a block abruptly (`fail`, a hard
assertion, a panic) fails the test that started it. Blocks still waiting
for a promise that nothing settles stay waiting. Each backend's harness
does this with its runtime after a test's body; the interpreter
implements this function, which does nothing elsewhere.

    @connected
    let finishAsyncWork(): Void {}

TODO Is this a better idea than inlining each case? We'd need to generate
`fn testFunction() { runTest(originalFunctionAsCallback) }` or some such.

    export let runTest(testFun: TestFun): Void {
      let test = new Test();
      testFun(test) orelse test.assert(false) { "error during test running" };
      test.softFailToHard();
    }

To produce JUnit XML, it's convenient to be able to escape XML text.

escapeXml takes a string and escapes it so that it has the same meaning as an
XML text node or attribute value.

    let escapeXml(s: String): String {
      let sb = new StringBuilder();
      let end = s.end;
      var emitted = String.begin;
      for (var i = String.begin; i < end; i = s.next(i)) {
        let c = s[i];
        let esc = when (c) {
          char'&' -> "&amp;";
          char'<' -> "&lt;";
          char'>' -> "&gt;";
          char"'" -> "&#39;";
          char'"' -> "&#34;";
          char'\n', char'\r', char'\t' -> continue;
          else -> if (c < char' ' || c == 0xFFFE || c == 0xFFFF) {
            // Illegal chars under XML 1.0, but also possibly trouble, so extra
            // escape them, in a way people aren't likely to accidentally
            // unescape.
            "[0x${c.toString(16)}]"
          } else {
            continue;
          }
        }
        sb.appendBetween(s, emitted, i);
        sb.append(esc);
        emitted = s.next(i);
      }
      if (emitted == String.begin) {
        s
      } else {
        sb.appendBetween(s, emitted, end);
        sb.toString()
      }
    }
