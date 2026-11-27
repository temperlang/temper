package testing;

import temper.core.Core;
import temper.std.testing.Test;

public final class TestingTest {
    static volatile int runs;
    static final int base;

    static {
        Core.keepStackTraces();
        TestingGlobal.initStatics();
        runs = 0;
        base = 10;
    }

    @org.junit.jupiter.api.Test
    public void twiceWorks() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int x = TestingGlobal.twice(2);
            runs = runs + x + base;
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void namesPunctuationQuotesAndÜnïcode() {
        Test t = new Test();
        Throwable failure = null;
        try {
            TestingGlobal.twice(runs);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, t::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void localsATestAssigns() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int n = 0;
            {
                int i = 0;
                while (i < 3) {
                    n = n + TestingGlobal.twice(i);
                    i = i + 1;
                }
            }
            runs = n;
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
