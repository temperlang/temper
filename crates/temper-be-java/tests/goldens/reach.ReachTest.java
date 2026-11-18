package reach;

import temper.std.testing.Test;

public final class ReachTest {
    @org.junit.jupiter.api.Test
    public void testParts() {
        Test test = new Test();
        try {
            TestShape shape = new TestSquare(ReachGlobal.forTests(2));
            ReachGlobal.addArea(shape);
            ReachGlobal.testCount = ReachGlobal.testCount + shape.area();
            {
                int actual = ReachGlobal.testCount;
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
