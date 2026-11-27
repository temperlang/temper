package host_error_tests;

import temper.core.Core;
import temper.std.testing.Test;

public final class HostErrorTestsTest {
    static {
        Core.keepStackTraces();
    }

    @org.junit.jupiter.api.Test
    public void hostErrorsArePanics() {
        Test test = new Test();
        Throwable failure = null;
        try {
            Runnable fn = () -> {
                int r;
                try {
                    r = HostErrorTestsGlobal.hostFails(1);
                } catch (Oops ignored) {
                    r = -1;
                }
                test.assert_(r != -1, () -> "expected r != -1");
            };
            test.assertPanics(fn, "host error 1");
            Runnable fn3 = () -> {
                int r;
                try {
                    r = HostErrorTestsGlobal.hostFails(2);
                } catch (Oops ignored2) {
                    r = -1;
                }
                test.assert_(r != -1, () -> "expected r != -1");
            };
            test.assertPanics(fn3, "host error 2");
            Runnable fn4 = () -> {
                int r;
                try {
                    r = HostErrorTestsGlobal.hostFails(3);
                } catch (Oops ignored3) {
                    r = -1;
                }
                test.assert_(r != -1, () -> "expected r != -1");
            };
            test.assertPanics(fn4, "host error 3");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void hostTestFailureIsNoPanic() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assertPanics(() -> HostErrorTestsGlobal.hostFailsTest());
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
