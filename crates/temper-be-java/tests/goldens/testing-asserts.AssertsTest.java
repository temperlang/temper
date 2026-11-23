package asserts;

import java.util.List;
import temper.core.Bubble;
import temper.core.Core;
import temper.std.testing.Test;

public final class AssertsTest {
    static void assertMoreThings(Test t, TestHelper helper, List<List<String>> strings) {
        {
            int i = 0;
            while (i < strings.size()) {
                {
                    int j = 0;
                    while (j < Core.listGet(strings, i).size()) {
                        {
                            int actual = helper.i;
                            int expected = helper.j;
                            t.assert_(actual == expected, () -> "expected helper.i == (" + expected + ") not (" + actual + ")");
                        }
                        String s = Core.listGet(Core.listGet(strings, i), j);
                        {
                            String actual = s;
                            String expected = "hi";
                            t.assert_(actual.equals(expected), () -> "expected s == (" + expected + ") not (" + actual + ")");
                        }
                        j = j + 1;
                    }
                }
                i = i + 1;
            }
        }
    }

    static int sumOf(TestHelper helper) {
        return helper.i + helper.j;
    }

    static int keptForTests(TestHelper helper) {
        return sumOf(helper);
    }

    static {
        Bubble.keepStackTraces();
    }

    @org.junit.jupiter.api.Test
    public void aTestCase() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(Double.compare(AssertsGlobal.normSquared(new Vec2(0.0, 0.0)), 0.0) == 0, () -> "Oh noes");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void aFailingTest() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(false, () -> "because it's wrong");
            TestHelper helper = new TestHelper(3, 2);
            assertMoreThings(test, helper, List.of(List.of("bye")));
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void exported() {
        Test test = new Test();
        Throwable failure = null;
        try {
            Vec2 vec = new Vec2(1.5, 2.5);
            {
                String actual = vec.toString();
                String expected = "(1.5, 2.5) size squared 8.5";
                test.assert_(actual.equals(expected), () -> "expected vec.toString() == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void moduleValues() {
        Test test = new Test();
        Throwable failure = null;
        try {
            {
                int length = AssertsGlobal.unitVecs.size();
                int i = 0;
                while (i < length) {
                    Vec2 vec = Core.listGet(AssertsGlobal.unitVecs, i);
                    i = i + 1;
                    {
                        double actual = AssertsGlobal.normSquared(vec);
                        double expected = 1.0;
                        test.assert_(Double.compare(actual, expected) == 0, () -> "expected normSquared(vec) == (" + Core.float64ToString(expected) + ") not (" + Core.float64ToString(actual) + ")");
                    }
                }
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
