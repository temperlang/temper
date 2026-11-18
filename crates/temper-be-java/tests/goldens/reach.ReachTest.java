package reach;

import temper.std.testing.Test;

public final class ReachTest {
    static final int testFactor;
    static volatile int testCount;

    static int forTests(int n) {
        return n * testFactor;
    }

    static void addArea(TestShape shape) {
        testCount = testCount + shape.area();
    }

    static {
        ReachGlobal.initStatics();
        testFactor = ReachGlobal.base * 2;
        testCount = 0;
    }

    @org.junit.jupiter.api.Test
    public void testParts() {
        Test test = new Test();
        try {
            TestShape shape = new TestSquare(forTests(2));
            addArea(shape);
            testCount = testCount + shape.area();
            {
                int actual = testCount;
                int expected = 32;
                test.assert_(actual == expected, () -> "expected testCount == (" + expected + ") not (" + actual + ")");
            }
            {
                int actual = ReachGlobal.checked;
                int expected = 3;
                test.assert_(actual == expected, () -> "expected checked == (" + expected + ") not (" + actual + ")");
            }
            {
                int actual = ReachGlobal.shownCount;
                int expected = 2;
                test.assert_(actual == expected, () -> "expected shownCount == (" + expected + ") not (" + actual + ")");
            }
            ReachGlobal.shownCount = 0;
            ReachGlobal.shown(0);
            {
                int actual = ReachGlobal.shownCount;
                int expected = 1;
                test.assert_(actual == expected, () -> "expected shownCount == (" + expected + ") not (" + actual + ")");
            }
        } finally {
            test.softFailToHard();
        }
    }
}
