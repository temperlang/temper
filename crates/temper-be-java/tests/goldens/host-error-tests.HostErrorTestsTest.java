package host_error_tests;

import temper.core.Bubble;
import temper.core.Core;
import temper.std.testing.Test;

public final class HostErrorTestsTest {
    @org.junit.jupiter.api.Test
    public void hostErrorIsNoPanic() {
        Test test = new Test();
        try {
            Runnable fn = () -> {
                int r;
                try {
                    r = HostErrorTestsGlobal.hostFails(1);
                } catch (Bubble ignored) {
                    r = -1;
                }
                test.assert_(r != -1, () -> "caught");
            };
            test.assertPanics(fn);
        } finally {
            Core.finishAsyncWork();
            test.softFailToHard();
        }
    }
}
