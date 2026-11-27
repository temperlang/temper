package host_failures_several_libraries;

import count_failures.CountFailuresGlobal;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;
import temper.core.Nullable;

public final class HostFailuresSeveralLibrariesGlobal {
    private HostFailuresSeveralLibrariesGlobal() {}

    static final Console console;

    public static int hostLookup(String key) throws NotFound {
        return HostFailuresSeveralLibrariesConnected.hostLookup(key);
    }

    static int total(String key, String count) {
        try {
            return hostLookup(key) + CountFailuresGlobal.parseCount(count);
        } catch (RuntimeException caught) {
            if (!(caught instanceof Failure)) {
                throw caught;
            }
            Failure e = (Failure) caught;
            console.log("caught " + e.getMessage());
            return e instanceof NotFound ? -1 : -2;
        }
    }

    static int reading(String key, String count) throws Unreadable {
        try {
            return hostLookup(key) + CountFailuresGlobal.parseCount(count);
        } catch (RuntimeException caught) {
            if (!(caught instanceof Failure)) {
                throw caught;
            }
            Failure failure = (Failure) caught;
            throw Core.withCause(new Unreadable(key), (RuntimeException) failure);
        }
    }

    static int nonzero(int n) throws Zero {
        if (n == 0) {
            throw new Zero();
        } else {
            return n;
        }
    }

    static int ratio(String key, String count) {
        try {
            return hostLookup(key) / nonzero(CountFailuresGlobal.parseCount(count));
        } catch (RuntimeException caught) {
            if (!(caught instanceof Failure)) {
                throw caught;
            }
            return -9;
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("host_failures_several_libraries"));
        console.log("before");
        console.log(total("a", "two") + " " + total("z", "two") + " " + total("a", "x"));
        int r;
        try {
            r = reading("a", "x");
        } catch (Unreadable e) {
            String t = e.getMessage() + ", caused by ";
            @Nullable Failure subject2 = Core.failureCause(e);
            @Nullable String subject = subject2 == null ? null : subject2.getMessage();
            console.log(t + (subject == null ? "nothing" : subject));
            r = 0;
        }
        console.log(r + " " + ratio("a", "two") + " " + ratio("a", "zero") + " " + ratio("a", "x") + " " + ratio("z", "two"));
        CountFailuresGlobal.parseCount("x");
        console.log("after");
    }
}
