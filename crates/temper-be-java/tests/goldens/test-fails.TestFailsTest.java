package test_fails;

import java.util.Objects;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Nullable;
import temper.std.testing.Test;

public final class TestFailsTest {
    static boolean tries(Runnable f) {
        boolean finished = true;
        try {
            f.run();
        } catch (Bubble ignored) {
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

    @org.junit.jupiter.api.Test
    public void passesBefore() {
        Test test = new Test();
        try {
            Runnable fn = () -> {
                {
                    @Nullable Integer actual = find("one");
                    int expected = 1;
                    test.assert_(Objects.equals(actual, expected), () -> "expected find(\"one\") == (" + expected + ") not (" + (actual == null ? "null" : Integer.toString(actual)) + ")");
                }
            };
            test.assert_(tries(fn), () -> "expected tries { assert(find(\"one\") == 1); }");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsPastAnOrelse() {
        Test test = new Test();
        try {
            Runnable fn = () -> {
                test.fail("failed on purpose");
                throw new AssertionError("panic");
            };
            tries(fn);
            test.fail("swallowed");
            throw new AssertionError("panic");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void narrowsAfterFail() {
        Test test = new Test();
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
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsBeforeNarrowing() {
        Test test = new Test();
        try {
            @Nullable Integer n = find("two");
            if (n == null) {
                test.fail("two not found");
                throw new AssertionError("panic");
            }
            test.assert_(n + 1 == 3, () -> "went on");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void collectsSoftFailures() {
        Test test = new Test();
        try {
            test.assert_(1 + 1 == 3, () -> "first");
            test.assert_(true, () -> "not shown");
            test.assert_(2 + 2 == 5, () -> "second");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsHardAfterSoftFailures() {
        Test test = new Test();
        try {
            test.assert_(false, () -> "soft before");
            test.fail("then hard");
            throw new AssertionError("panic");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsAHardAssertionPastAnOrelse() {
        Test test = new Test();
        try {
            tries(() -> test.assertHard(1 + 1 == 3, () -> "hard assertion"));
            test.fail("swallowed");
            throw new AssertionError("panic");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void hardensSoftFailuresPastAnOrelse() {
        Test test = new Test();
        try {
            test.assert_(false, () -> "soft only");
            tries(() -> test.softFailToHard());
            test.fail("swallowed");
            throw new AssertionError("panic");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void passesAfter() {
        Test test = new Test();
        try {
            {
                @Nullable Integer actual = find("one");
                int expected = 1;
                test.assert_(Objects.equals(actual, expected), () -> "expected find(\"one\") == (" + expected + ") not (" + (actual == null ? "null" : Integer.toString(actual)) + ")");
            }
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }
}
