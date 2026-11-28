package declared_errors;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class DeclaredErrorsGlobal {
    private DeclaredErrorsGlobal() {}

    static int lookup(String key) throws NotFound {
        if (key.equals("a")) {
            return 1;
        }
        if (key.equals("b")) {
            return 2;
        }
        throw new NotFound(key);
    }

    static int sum(String a, String b) throws NotFound {
        int x = lookup(a);
        int y = lookup(b);
        return x + y;
    }

    static String shown(String a, String b) {
        int total;
        @Nullable NotFound caught = null;
        @Nullable Integer held = null;
        try {
            held = sum(a, b);
        } catch (NotFound failure) {
            caught = failure;
        }
        if (held == null) {
            NotFound e = caught;
            return "no " + e.key;
        } else {
            total = held;
        }
        return Integer.toString(total);
    }

    static int sign(String text) throws BadSign {
        if (text.equals("+")) {
            return 1;
        } else if (text.equals("-")) {
            return -1;
        } else {
            throw new BadSign(text);
        }
    }

    static int parseSign(String text) throws SignException {
        if (text.equals("")) {
            throw new EmptyText();
        }
        return sign(text);
    }

    static String describe(SignException e) {
        if (e instanceof EmptyText) {
            return "empty";
        } else if (e instanceof BadSign) {
            return "bad sign " + ((BadSign) e).text;
        } else {
            throw new AssertionError("panic");
        }
    }

    static int rethrown(NotFound p) throws NotFound {
        throw p;
    }

    static int setting(String name) throws ConfigException {
        try {
            return lookup(name);
        } catch (NotFound e) {
            throw Core.withCause(new ConfigException(name + ": missing " + e.key), e);
        }
    }

    static String pair(String a, String b) {
        try {
            int x = lookup(a);
            int y;
            try {
                y = lookup(b);
            } catch (NotFound ignored) {
                y = 0;
            }
            return x + "+" + y;
        } catch (NotFound e) {
            return "outer " + e.key;
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("declared_errors"));
        int found;
        try {
            found = lookup("a");
        } catch (NotFound ignored) {
            found = -1;
        }
        int missing;
        try {
            missing = lookup("z");
        } catch (NotFound ignored2) {
            missing = -1;
        }
        console.log(found + " " + missing);
        int n;
        try {
            n = lookup("q");
        } catch (NotFound e) {
            console.log("not found: " + e.key);
            n = 0;
        }
        int m;
        try {
            m = lookup("b");
        } catch (NotFound ignored3) {
            m = -1;
        }
        console.log(n + " " + m);
        console.log(shown("a", "b") + " " + shown("a", "x") + " " + shown("y", "z"));
        {
            List<String> list = List.of("+", "-", "", "x");
            int length = list.size();
            int i = 0;
            while (i < length) {
                String t = Core.listGet(list, i);
                i = i + 1;
                int s;
                @Nullable SignException caught = null;
                @Nullable Integer held = null;
                try {
                    held = parseSign(t);
                } catch (SignException failure) {
                    caught = failure;
                }
                if (held == null) {
                    SignException e2 = caught;
                    console.log(describe(e2));
                    continue;
                } else {
                    s = held;
                }
                console.log(Integer.toString(s));
            }
        }
        int code;
        try {
            code = parseSign("?");
        } catch (SignException e3) {
            code = e3 instanceof EmptyText ? 0 : e3 instanceof BadSign ? 9 : Core.throwAssertionError("panic");
        }
        console.log(Integer.toString(code));
        NotFound kept = new NotFound("none");
        int v;
        try {
            v = lookup("k");
        } catch (NotFound e4) {
            kept = e4;
            v = -1;
        }
        List<NotFound> problems = List.of(new NotFound("x"), kept);
        console.log(v + " " + Core.listJoinObj(problems, ",", p -> p.key));
        int w;
        try {
            w = rethrown(kept);
        } catch (NotFound e5) {
            console.log("again " + e5.key);
            w = 0;
        }
        int port;
        try {
            port = setting("port");
        } catch (ConfigException e6) {
            console.log(e6.setting);
            port = -1;
        }
        int first;
        try {
            first = setting("a");
        } catch (ConfigException ignored4) {
            first = -1;
        }
        console.log(port + " " + first);
        console.log(pair("a", "z") + " " + pair("z", "a"));
        int total = 0;
        {
            List<String> list2 = List.of("a", "b", "c", "a");
            int length2 = list2.size();
            int i2 = 0;
            while (i2 < length2) {
                String k = Core.listGet(list2, i2);
                i2 = i2 + 1;
                int t3 = total;
                int t2;
                @Nullable Integer held2 = null;
                try {
                    held2 = lookup(k);
                } catch (NotFound ignored5) {
                }
                if (held2 == null) {
                    break;
                } else {
                    t2 = held2;
                }
                total = t3 + t2;
            }
        }
        console.log(Integer.toString(total));
    }
}
