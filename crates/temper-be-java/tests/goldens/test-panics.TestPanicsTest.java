package test_panics;

import java.util.List;
import temper.core.Bubble;
import temper.core.Core;
import temper.std.testing.Test;

public final class TestPanicsTest {
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
                int actual = 1 + 1;
                int expected = 2;
                test.assert_(actual == expected, () -> "expected 1 + 1 == (" + expected + ") not (" + actual + ")");
            }
        } finally {
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void panicsReadingPastTheEnd() {
        Test test = new Test();
        try {
            int n;
            try {
                n = at(List.of(1, 2), 5);
            } catch (Bubble ignored) {
                n = -1;
            }
            {
                int actual = n;
                int expected = -1;
                test.assert_(actual == expected, () -> "expected n == (" + expected + ") not (" + actual + ")");
            }
        } finally {
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void panicsOutright() {
        Test test = new Test();
        try {
            throw new AssertionError("panic");
        } finally {
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void failsAnAssertion() {
        Test test = new Test();
        try {
            test.assert_(1 + 1 == 3, () -> "wrong on purpose");
        } finally {
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void passesAfter() {
        Test test = new Test();
        try {
            {
                int actual = at(List.of(1, 2), 1);
                int expected = 2;
                test.assert_(actual == expected, () -> "expected at([1, 2], 1) == (" + expected + ") not (" + actual + ")");
            }
        } finally {
            test.softFailToHard();
        }
    }
}
