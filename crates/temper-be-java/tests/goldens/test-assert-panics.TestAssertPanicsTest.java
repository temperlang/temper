package test_assert_panics;

import java.util.List;
import temper.core.Bubble;
import temper.core.Core;
import temper.std.testing.Test;

public final class TestAssertPanicsTest {
    static int at(List<Integer> ls, int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return Core.listGet(ls, i);
    }

    @org.junit.jupiter.api.Test
    public void passesBefore() {
        Test test = new Test();
        try {
            {
                int actual = at(List.of(1, 2), 1);
                int expected = 2;
                test.assert_(actual == expected, () -> "expected at([1, 2], 1) == (" + expected + ") not (" + actual + ")");
            }
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void passesWhenTheBlockPanics() {
        Test test = new Test();
        try {
            test.assertPanics(() -> at(List.of(1, 2), 5));
            test.assertPanics(() -> Core.throwAssertionError("panic"));
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void passesPastAnOrelse() {
        Test test = new Test();
        try {
            Runnable fn = () -> {
                int n;
                try {
                    n = at(List.of(1, 2), 5);
                } catch (Bubble ignored) {
                    n = -1;
                }
                test.assert_(n == -1, () -> "caught by an orelse");
            };
            test.assertPanics(fn);
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWhenTheBlockReturns() {
        Test test = new Test();
        try {
            test.assertPanics(() -> at(List.of(1, 2), 0));
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWithItsMessage() {
        Test test = new Test();
        try {
            test.assertPanics(() -> at(List.of(1, 2), 1), "reading index 1 panics");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWhenTheBlockBubbles() {
        Test test = new Test();
        try {
            test.assertPanics(() -> at(List.of(1, 2), 99));
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsWithItsMessageWhenTheBlockBubbles() {
        Test test = new Test();
        try {
            test.assertPanics(() -> at(List.of(1, 2), 99), "reading index 99 panics");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void goesOnAfterFailing() {
        Test test = new Test();
        try {
            test.assertPanics(() -> at(List.of(1, 2), 0));
            test.assert_(false, () -> "went on");
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void nests() {
        Test test = new Test();
        try {
            Runnable fn = () -> {
                test.assertPanics(() -> Core.throwAssertionError("panic"));
                test.assertPanics(() -> at(List.of(1, 2), 0), "inner returns");
                throw new AssertionError("panic");
            };
            test.assertPanics(fn);
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void keepsWhatTheBlockChanged() {
        Test test = new Test();
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
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsOnAPanicAfterTheBlock() {
        Test test = new Test();
        try {
            test.assertPanics(() -> Core.throwAssertionError("panic"));
            at(List.of(1, 2), 7);
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void endsOnAFailInTheBlock() {
        Test test = new Test();
        try {
            Runnable fn = () -> {
                test.fail("failed inside");
                throw new AssertionError("panic");
            };
            test.assertPanics(fn);
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
                int actual = at(List.of(1, 2), 0);
                int expected = 1;
                test.assert_(actual == expected, () -> "expected at([1, 2], 0) == (" + expected + ") not (" + actual + ")");
            }
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }
}
