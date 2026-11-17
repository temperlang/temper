package asserts;

import java.util.List;
import temper.core.Core;
import temper.std.testing.Test;

public final class AssertsGlobal {
    private AssertsGlobal() {}

    static final List<Vec2> unitVecs;

    static double normSquared(Vec2 vec) {
        return vec.x * vec.x + vec.y * vec.y;
    }

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
        String name = "case";
        unitVecs = List.of(new Vec2(1.0, 0.0), new Vec2(0.0, 1.0));
    }
}
