package test_fails;

import java.util.Objects;
import temper.core.Core;
import temper.core.Nullable;
import temper.std.testing.Test;

public final class TestFailsTest {
    static boolean tries(Runnable f) {
        boolean finished = true;
        try {
            f.run();
        } catch (Oops ignored) {
            finished = false;
        }
        return finished;
    }

    static @Nullable Integer find(String key) {
        if (key.equals("one")) {
            return 1;
        } else {
            return null;
        }
    }

    static {
        Core.keepStackTraces();
    }

    @org.junit.jupiter.api.Test
    public void passesBefore() {
        Test test = new Test();
        Throwable failure = null;
        try {
            Runnable fn = () -> {
                {
                    @Nullable Integer actual = find("one");
                    int expected = 1;
                    test.assert_(Objects.equals(actual, expected), () -> "expected find(\"one\") == (" + expected + ") not (" + (actual == null ? "null" : Integer.toString(actual)) + ")");
                }
            };
            test.assert_(tries(fn), () -> "expected tries { assert(find(\"one\") == 1); }");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsPastAnOrelse() {
        Test test = new Test();
        Throwable failure = null;
        try {
            Runnable fn = () -> {
                test.fail("failed on purpose");
                throw new AssertionError("panic");
            };
            tries(fn);
            test.fail("swallowed");
            throw new AssertionError("panic");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void narrowsAfterFail() {
        Test test = new Test();
        Throwable failure = null;
        try {
            @Nullable Integer n = find("one");
            if (n == null) {
                test.fail("one not found");
                throw new AssertionError("panic");
            }
            {
                int actual = n + 1;
                int expected = 2;
                test.assert_(actual == expected, () -> "expected n + 1 == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsBeforeNarrowing() {
        Test test = new Test();
        Throwable failure = null;
        try {
            @Nullable Integer n = find("two");
            if (n == null) {
                test.fail("two not found");
                throw new AssertionError("panic");
            }
            test.assert_(n + 1 == 3, () -> "went on");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void collectsSoftFailures() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(1 + 1 == 3, () -> "first");
            test.assert_(true, () -> "not shown");
            test.assert_(2 + 2 == 5, () -> "second");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsHardAfterSoftFailures() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(false, () -> "soft before");
            test.fail("then hard");
            throw new AssertionError("panic");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsAHardAssertionPastAnOrelse() {
        Test test = new Test();
        Throwable failure = null;
        try {
            tries(() -> test.assertHard(1 + 1 == 3, () -> "hard assertion"));
            test.fail("swallowed");
            throw new AssertionError("panic");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void hardensSoftFailuresPastAnOrelse() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(false, () -> "soft only");
            tries(() -> test.softFailToHard());
            test.fail("swallowed");
            throw new AssertionError("panic");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void passesAfter() {
        Test test = new Test();
        Throwable failure = null;
        try {
            {
                @Nullable Integer actual = find("one");
                int expected = 1;
                test.assert_(Objects.equals(actual, expected), () -> "expected find(\"one\") == (" + expected + ") not (" + (actual == null ? "null" : Integer.toString(actual)) + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
