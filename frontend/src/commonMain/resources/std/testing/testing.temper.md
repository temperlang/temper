# Temper test framework

## Test instance

We currently convert assert and check macro calls into method calls on a `Test`
instance.

    export class Test {

Recommended default Temper assert is soft, meaning that it records failures for
reporting but doesn't immediately end test execution on a false value. This lets
you check multiple conditions more easily.

      @connected("Test::assert")
      public assert(success: Boolean, message: fn (): String): Void {
        if (!success) {
          _passing = false;
          _messages.add(message());
        }
      }

Typical hard asserts that end the test on false condition also are available.

      @connected("Test::assertHard")
      public assertHard(
        success: Boolean,
        message: fn (): String,
      ): Void throws Bubble {
        assert(success, message);
        if (!success) {
          // Attempt to distinguish assert fails from others.
          // Sadly, they can still orelse an assert failure, so this isn't
          // flawless.
          _failedOnAssert = true;
          bail();
        }
      }

Harden and end current test on any pending failure if not previously hardened.
Backends typically insert calls to this if needed, but you can also call it
manually at any desired point in your test.

      public softFailToHard(): Void throws Bubble {
        if (hasUnhandledFail) {
          _failedOnAssert = true;
          bail();
        }
      }

Provide a bailing `Bubble` method here that enables backends to customize
message delivery on failure.

      @connected("Test::bail")
      private bail(): Never<Void> throws Bubble {
        bubble()
      }

You can check the current passing state of the test at any time. A test is
currently passing if all soft checks and hard asserts have been successful.

TODO Does this need to be function call syntax for macro purposes?

      @connected("Test::passing")
      public get passing(): Boolean { _passing }

Messages access is presented as a function because it likely allocates. Also,
messages might be automatically constructed in some cases, so it's possibly
unwise to depend on their exact formatting.

      @connected("Test::messages")
      public messages(): List<String> { _messages.toList() }

### Backend helper methods

Avoid using backend helper methods in user code. Their behavior might be
unreliable on some backends and/or have high risk of changing in future releases
of Temper.

      @connected("Test::failedOnAssert")
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

    export let TestCase = Pair<TestName, TestFun>;
    export let TestFailureMessage = String;
    export let TestFun = fn (Test): Void throws Bubble;
    export let TestName = String;
    export let TestResult = Pair<TestName, List<TestFailureMessage>>;

    @connected("::processTestCases")
    export let processTestCases(testCases: List<TestCase>): List<TestResult> {
      testCases.map { (testCase): TestResult =>
        let { key, value as fun } = testCase;
        let test = new Test();
        // Actually call the test.
        var hadBubble = false;
        fun(test) orelse do { hadBubble = true };
        // Now get the messages.
        let messages = test.messages();
        let failures: List<TestFailureMessage> = if (test.passing && !hadBubble) {
          []
        } else if (hadBubble && !test.failedOnAssert) {
          // Despite having 1+ failure messages, we seem to have failed on some
          // Bubble separate from asserts, so add that on.
          let allMessages = messages.toListBuilder();
          allMessages.add("Bubble");
          allMessages.toList()
        } else {
          messages
        };
        // Package up with test name.
        new Pair(key, failures)
      }
    }

    @connected("::reportTestResults")
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

    @connected("::runTestCases")
    export let runTestCases(testCases: List<TestCase>): String {
      let report = new StringBuilder();
      reportTestResults(processTestCases(testCases)) { line =>
        report.append(line);
        report.append("\n");
      }
      report.toString()
    }

TODO Is this a better idea than inlining each case? We'd need to generate
`fn testFunction() { runTest(originalFunctionAsCallback) }` or some such.

    export let runTest(testFun: TestFun): Void throws Bubble {
      let test = new Test();
      testFun(test) orelse test.assert(false) { "bubble during test running" };
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
