package failures_interop;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;
import temper.core.Nullable;

public final class FailuresInteropGlobal {
    private FailuresInteropGlobal() {}

    public static int lookup(String key) throws NotFound {
        if (key.equals("a")) {
            return 1;
        }
        throw new NotFound(key);
    }

    public static int sign(String text) throws SignException {
        if (text.equals("+")) {
            return 1;
        } else if (text.equals("-")) {
            return -1;
        } else if (text.equals("")) {
            throw new EmptyText();
        } else {
            throw new BadSign(text);
        }
    }

    public static int setting(String name) throws LookupFailed {
        try {
            return lookup(name);
        } catch (NotFound failure) {
            throw Core.withCause(new LookupFailed(name), failure);
        }
    }

    public static int madeBefore(String name) throws LookupFailed {
        LookupFailed failure = new LookupFailed(name);
        try {
            return lookup(name);
        } catch (NotFound failure2) {
            throw Core.withCause(failure, failure2);
        }
    }

    public static int thrownAgain(String name) throws LookupFailed {
        try {
            return setting(name);
        } catch (LookupFailed e) {
            try {
                return lookup("q");
            } catch (NotFound failure) {
                throw Core.withCause(e, failure);
            }
        }
    }

    public static int risky(int n) throws NotFound {
        if (n > 1) {
            throw new AssertionError("panic");
        }
        return lookup("a");
    }

    public static String explained(String name) {
        try {
            return Integer.toString(setting(name));
        } catch (LookupFailed e) {
            @Nullable Failure cause = Core.failureCause(e);
            String why = cause != null ? cause.getMessage() : "nothing";
            return e.getMessage() + " for " + e.name + ", from " + why;
        }
    }

    public static String signMessage(String text) {
        try {
            return Integer.toString(sign(text));
        } catch (SignException e) {
            return e.getMessage();
        }
    }

    public static String earlyCause(String name) {
        try {
            return Integer.toString(madeBefore(name));
        } catch (LookupFailed e) {
            @Nullable Failure cause = Core.failureCause(e);
            return cause != null ? cause.getMessage() : "nothing";
        }
    }

    public static String signName(SignException e) {
        if (e instanceof BadSign) {
            return "bad sign";
        } else if (e instanceof EmptyText) {
            return "empty text";
        } else {
            throw new AssertionError("panic");
        }
    }

    public static int mixed(String key, String text) {
        try {
            return lookup(key) + 10 / sign(text);
        } catch (FailuresInteropException ignored) {
            return -1;
        }
    }

    public static String firstSign(List<String> texts) {
        {
            int length = texts.size();
            int i = 0;
            while (i < length) {
                String t = Core.listGet(texts, i);
                i = i + 1;
                int s;
                @Nullable SignException caught = null;
                @Nullable Integer held = null;
                try {
                    held = sign(t);
                } catch (SignException failure) {
                    caught = failure;
                }
                if (held == null) {
                    SignException e = caught;
                    if (e instanceof BadSign) {
                        return "bad " + ((BadSign) e).text;
                    }
                    continue;
                } else {
                    s = held;
                }
                return Integer.toString(s);
            }
        }
        return "none";
    }

    public static String firstMissing(List<String> keys) {
        {
            int length = keys.size();
            int i = 0;
            while (i < length) {
                String k = Core.listGet(keys, i);
                i = i + 1;
                int n;
                @Nullable NotFound caught = null;
                @Nullable Integer held = null;
                try {
                    held = lookup(k);
                } catch (NotFound failure) {
                    caught = failure;
                }
                if (held == null) {
                    NotFound e = caught;
                    return e.key;
                } else {
                    n = held;
                }
                if (n < 0) {
                    break;
                }
            }
        }
        return "none";
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("failures_interop"));
        console.log(lookup("a") + " " + sign("-") + " " + setting("a"));
        console.log(mixed("a", "") + " " + mixed("z", "+") + " " + mixed("a", "-"));
        console.log(explained("port") + "; " + explained("a") + "; " + signMessage("x"));
        console.log(earlyCause("soon") + "; " + firstMissing(List.of("a", "b", "c")) + " " + firstMissing(List.of("a")));
        console.log(firstSign(List.of("", "+")) + " " + firstSign(List.of("", "?")) + " " + firstSign(List.of()));
    }
}
