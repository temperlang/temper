package test_assert_panics;

import java.util.List;
import temper.core.Core;
import temper.std.testing.Test;

public final class TestAssertPanicsTest {
    static int at(List<Integer> ls, int i) throws Unlucky {
        if (i == 99) {
            throw new Unlucky();
        }
        return Core.listGet(ls, i);
    }

    static int divide(int a, int b) {
        return a / b;
    }

    static long remainder(long a, long b) {
        return a % b;
    }

    static Square squared(Shape s) {
        return Core.cast(Square.class, s);
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
    public void passesWhenTheBlockPanics() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> at(List.of(1, 2), 5));
            test.assertPanics(() -> Core.throwAssertionError("panic"));
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void passesWhenDivisionByZeroOrACastPanics() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> divide(1, 0));
            test.assertPanics(() -> remainder(1L, 0L));
            test.assertPanics(() -> squared(new Circle()));
            squared(new Square());
            {
                int actual = divide(-7, 2);
                int expected = -3;
                test.assert_(actual == expected, () -> "expected divide(-7, 2) == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void passesPastAnOrelse() {
        Test test = new Test();
        Throwable failure = null;
        try {
            Runnable fn = () -> {
                int n;
                try {
                    n = at(List.of(1, 2), 5);
                } catch (Unlucky ignored) {
                    n = -1;
                }
                test.assert_(n == -1, () -> "caught by an orelse");
            };
            test.assertPanics(fn);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWhenTheBlockReturns() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> at(List.of(1, 2), 0));
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWithItsMessage() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> at(List.of(1, 2), 1), "reading index 1 panics");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWhenTheBlockThrows() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> at(List.of(1, 2), 99));
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWithItsMessageWhenTheBlockThrows() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> at(List.of(1, 2), 99), "reading index 99 panics");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void goesOnAfterFailing() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> at(List.of(1, 2), 0));
            test.assert_(false, () -> "went on");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void nests() {
        Test test = new Test();
        Throwable failure = null;
        try {
            Runnable fn = () -> {
                test.assertPanics(() -> Core.throwAssertionError("panic"));
                test.assertPanics(() -> at(List.of(1, 2), 0), "inner returns");
                throw new AssertionError("panic");
            };
            test.assertPanics(fn);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void keepsWhatTheBlockChanged() {
        Test test = new Test();
        Throwable failure = null;
        try {
            class NCell {
                int value;
            }
            final NCell n = new NCell();
            n.value = 0;
            Runnable fn = () -> {
                n.value = n.value + 1;
                throw new AssertionError("panic");
            };
            test.assertPanics(fn);
            test.assert_(n.value == 1, () -> "lost the change");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsOnAPanicAfterTheBlock() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> Core.throwAssertionError("panic"));
            at(List.of(1, 2), 7);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void endsOnAFailInTheBlock() {
        Test test = new Test();
        Throwable failure = null;
        try {
            Runnable fn = () -> {
                test.fail("failed inside");
                throw new AssertionError("panic");
            };
            test.assertPanics(fn);
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
    public void failsWhenItsBodyThrows() {
        Test test = new Test();
        Throwable failure = null;
        try {
            at(List.of(1, 2), 99);
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
                int actual = at(List.of(1, 2), 0);
                int expected = 1;
                test.assert_(actual == expected, () -> "expected at([1, 2], 0) == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
