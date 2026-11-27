package test_panics;

import java.util.List;
import temper.core.Core;
import temper.std.testing.Test;

public final class TestPanicsTest {
    static int at(List<Integer> ls, int i) throws Oops {
        if (i == 99) {
            throw new Oops();
        }
        return Core.listGet(ls, i);
    }

    static {
        Core.keepStackTraces();
    }

    @org.junit.jupiter.api.Test
    public void passesBefore() {
        Test test = new Test();
        Throwable failure = null;
        try {
            {
                int actual = 1 + 1;
                int expected = 2;
                test.assert_(actual == expected, () -> "expected 1 + 1 == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void panicsReadingPastTheEnd() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int n;
            try {
                n = at(List.of(1, 2), 5);
            } catch (Oops ignored) {
                n = -1;
            }
            {
                int actual = n;
                int expected = -1;
                test.assert_(actual == expected, () -> "expected n == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void diesOutright() {
        Test test = new Test();
        Throwable failure = null;
        try {
            throw new AssertionError("panic");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsAnAssertion() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(1 + 1 == 3, () -> "wrong on purpose");
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
                int actual = at(List.of(1, 2), 1);
                int expected = 2;
                test.assert_(actual == expected, () -> "expected at([1, 2], 1) == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void diesAfterFailing() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(1 + 1 == 3, () -> "failed before");
            throw new AssertionError("panic");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
