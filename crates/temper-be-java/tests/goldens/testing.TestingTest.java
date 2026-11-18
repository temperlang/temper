package testing;

import temper.std.testing.Test;

public final class TestingTest {
    static volatile int runs;
    static final int base;

    static {
        TestingGlobal.initStatics();
        runs = 0;
        base = 10;
    }

    @org.junit.jupiter.api.Test
    public void twiceWorks() {
        Test test = new Test();
        try {
            int x = TestingGlobal.twice(2);
            runs = runs + x + base;
        } finally {
            test.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void namesPunctuationQuotesAndÜnïcode() {
        Test t = new Test();
        try {
            TestingGlobal.twice(runs);
        } finally {
            t.softFailToHard();
        }
    }

    @org.junit.jupiter.api.Test
    public void localsATestAssigns() {
        Test test = new Test();
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
        } finally {
            test.softFailToHard();
        }
    }
}
