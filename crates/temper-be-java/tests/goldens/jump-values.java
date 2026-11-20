package jump_values;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class JumpValuesGlobal {
    private JumpValuesGlobal() {}

    static final Console console;
    static volatile int steps;

    static int step(int n) {
        synchronized (JumpValuesGlobal.class) {
            steps = steps + 1;
        }
        console.log("step " + n);
        return n;
    }

    static int pair(int a, int b) {
        return a * 10 + b;
    }

    static int parse(String s) {
        return Core.stringToInt(s);
    }

    static @Nullable Integer next(@Nullable Integer y) {
        int x;
        if (y == null) {
            return null;
        } else {
            x = y;
        }
        return x + 1;
    }

    static int sumPresent(List<@Nullable Integer> ys) {
        int sum = 0;
        {
            int i = 0;
            while (i < ys.size()) {
                int t2 = sum;
                int t;
                @Nullable Integer subject = Core.listGet(ys, i);
                if (subject == null) {
                    i = i + 1;
                    continue;
                } else {
                    t = subject;
                }
                sum = t2 + t;
                i = i + 1;
            }
        }
        return sum;
    }

    static int sumUntilBad(List<String> words) {
        int sum = 0;
        int i = 0;
        while (i < words.size()) {
            int t2 = sum;
            int t;
            @Nullable Integer held = null;
            try {
                held = parse(Core.listGet(words, i));
            } catch (Bubble ignored) {
            }
            if (held == null) {
                break;
            } else {
                t = held;
            }
            sum = t2 + t;
            i = i + 1;
        }
        return sum;
    }

    static int parsedOr(String s) {
        int t;
        @Nullable Integer held = null;
        try {
            held = parse(s);
        } catch (Bubble ignored) {
        }
        if (held == null) {
            return -1;
        } else {
            t = held;
        }
        return t;
    }

    static @Nullable Integer after(int i) {
        if (i < 3) {
            return i + 1;
        } else {
            return null;
        }
    }

    static String stepped() {
        String seen = "";
        {
            int i = 0;
            while (i < 6) {
                if (i == 1) {
                    @Nullable Integer subject = after(i);
                    i = subject == null ? 9 : subject;
                    continue;
                }
                seen = seen + i;
                @Nullable Integer subject2 = after(i);
                i = subject2 == null ? 9 : subject2;
            }
        }
        return seen;
    }

    static int paired(@Nullable Integer y) {
        int t2 = step(1);
        int t;
        if (y == null) {
            return -2;
        } else {
            t = y;
        }
        return pair(t2, t);
    }

    static List<Integer> listed(@Nullable Integer y, @Nullable Integer z) {
        int t;
        if (y == null) {
            return List.of();
        } else {
            t = y;
        }
        int t3 = t;
        int t2;
        if (z == null) {
            return List.of(9);
        } else {
            t2 = z;
        }
        return List.of(1, t3, 3, t2);
    }

    static String shown(@Nullable Integer y) {
        int t;
        if (y == null) {
            return "none";
        } else {
            t = y;
        }
        return "y=" + t;
    }

    static String both(boolean t, @Nullable Integer y) {
        boolean ok;
        if (t && y != null) {
            ok = true;
        } else {
            return "no";
        }
        return "ok " + ok;
    }

    static String sign(int n) {
        int t;
        if (n > 0) {
            t = 1;
        } else if (n < 0) {
            t = -1;
        } else {
            return "zero";
        }
        return Integer.toString(pair(0, t));
    }

    static void firstWord(@Nullable String text) {
        String t;
        if (text == null) {
            return;
        } else {
            t = text;
        }
        console.log("first " + Core.listGet(Core.stringSplit(t, " "), 0));
    }

    static int early() {
        return 7;
    }

    static int partly() {
        step(2);
        return step(3);
    }

    static String doJumps(List<@Nullable Integer> ys) {
        String seen = "";
        {
            int i = 0;
            while (i < ys.size()) {
                int v;
                @Nullable Integer subject = Core.listGet(ys, i);
                if (subject == null) {
                    seen = seen + "a";
                    if (i == 1) {
                        i = i + 1;
                        continue;
                    }
                    seen = seen + "b";
                    break;
                } else {
                    v = subject;
                }
                seen = seen + v;
                i = i + 1;
            }
        }
        return seen;
    }

    static String doReturn(@Nullable Integer y, int z) {
        int v;
        if (y == null) {
            if (z > 0) {
                if (z > 5) {
                    return "big";
                }
                return "pos";
            }
            v = 0;
        } else {
            v = y;
        }
        return "v" + v;
    }

    static int trio(int a, int b, int c) {
        return a + b + c;
    }

    static int ordered(List<@Nullable Integer> ys) {
        int sum = 0;
        {
            int i = 0;
            while (i < ys.size()) {
                int t2 = sum;
                int t3 = step(10 + i);
                int t;
                @Nullable Integer subject = Core.listGet(ys, i);
                if (subject == null) {
                    step(20 + i);
                    i = i + 1;
                    continue;
                } else {
                    t = subject;
                }
                sum = t2 + trio(t3, t, step(30 + i));
                i = i + 1;
            }
        }
        return sum;
    }

    static String nested(List<@Nullable Integer> xs, List<@Nullable Integer> ys) {
        String seen = "";
        {
            int i = 0;
            while (i < xs.size()) {
                int v;
                @Nullable Integer subject = Core.listGet(xs, i);
                if (subject == null) {
                    @Nullable Integer subject2 = Core.listGet(ys, i);
                    if (subject2 == null) {
                        break;
                    } else {
                        v = subject2;
                    }
                } else {
                    v = subject;
                }
                seen = seen + v;
                i = i + 1;
            }
        }
        return seen;
    }

    static String gridded(List<List<@Nullable Integer>> rows) {
        String seen = "";
        {
            int i = 0;
            outer: while (i < rows.size()) {
                List<@Nullable Integer> row = Core.listGet(rows, i);
                {
                    int j = 0;
                    while (j < row.size()) {
                        int v;
                        @Nullable Integer subject = Core.listGet(row, j);
                        if (subject == null) {
                            break outer;
                        } else {
                            v = subject;
                        }
                        int w;
                        @Nullable Integer subject2 = v < 0 ? null : Integer.valueOf(v);
                        if (subject2 == null) {
                            seen = seen + "-";
                            i = i + 1;
                            continue outer;
                        } else {
                            w = subject2;
                        }
                        seen = seen + w;
                        j = j + 1;
                    }
                }
                seen = seen + "|";
                i = i + 1;
            }
        }
        return seen;
    }

    static String firstBig(List<List<@Nullable Integer>> rows, List<@Nullable Integer> keys) {
        String seen = "";
        {
            int i = 0;
            outer: while (i < rows.size()) {
                @Nullable Integer subject = Core.listGet(keys, i);
                int k;
                if (subject == null) {
                    List<@Nullable Integer> row = Core.listGet(rows, i);
                    int first = -1;
                    {
                        int j = 0;
                        while (j < row.size()) {
                            int v;
                            @Nullable Integer subject2 = Core.listGet(row, j);
                            if (subject2 == null) {
                                i = i + 1;
                                continue outer;
                            } else {
                                v = subject2;
                            }
                            if (v > 5) {
                                break outer;
                            }
                            if (v > 2) {
                                first = v;
                                break;
                            }
                            j = j + 1;
                        }
                    }
                    k = first;
                } else {
                    k = subject;
                }
                seen = seen + k + ",";
                i = i + 1;
            }
        }
        return seen;
    }

    static String viaOrelse(List<String> words) {
        String seen = "";
        {
            int i = 0;
            while (i < words.size()) {
                int n;
                @Nullable Integer held = null;
                try {
                    held = parse(Core.listGet(words, i));
                } catch (Bubble ignored) {
                }
                if (held == null) {
                    seen = seen + "!";
                    if (i > 2) {
                        break;
                    }
                    i = i + 1;
                    continue;
                } else {
                    n = held;
                }
                seen = seen + n;
                i = i + 1;
            }
        }
        return seen;
    }

    static String viaIf(int n) {
        String s;
        if (n > 0) {
            s = "pos";
        } else {
            if (n < -5) {
                return "low";
            }
            s = "neg";
        }
        return "s=" + s;
    }

    static {
        console = Core.getConsole(Logger.getLogger("jump_values"));
        steps = 0;
        @Nullable Integer part = next(1);
        String t = (part == null ? "null" : Integer.toString(part)) + " ";
        @Nullable Integer part2 = next(null);
        console.log(t + (part2 == null ? "null" : Integer.toString(part2)));
        console.log(sumPresent(Core.listOf(1, null, 3)) + " " + sumUntilBad(List.of("1", "2", "x", "4")));
        console.log(parsedOr("12") + " " + parsedOr("x") + " " + stepped());
        console.log(paired(3) + " " + paired(null));
        console.log(listed(2, 4).size() + " " + listed(null, 4).size() + " " + Core.listGet(listed(2, null), 0));
        console.log(shown(5) + " " + shown(null));
        console.log(both(true, 1) + " " + both(true, null) + " " + both(false, 1));
        console.log(sign(4) + " " + sign(-4) + " " + sign(0));
        firstWord("hello there");
        firstWord(null);
        console.log(doJumps(Core.listOf(1, null, 2, null, 3)) + " " + doReturn(1, 0) + " " + doReturn(null, 9) + " " + doReturn(null, 1) + " " + doReturn(null, 0));
        console.log(Integer.toString(ordered(Core.listOf(1, null))));
        console.log(nested(Core.listOf(1, null, null, 4), Core.listOf(0, 7, null, 0)) + " " + viaOrelse(List.of("1", "x", "2", "y", "z", "3")));
        console.log(gridded(List.of(Core.listOf(1, 2), Core.listOf(3, -1, 4), Core.listOf(5, null, 6), Core.listOf(7))) + " " + firstBig(List.of(Core.listOf(1, 3), Core.listOf(null, 1), Core.listOf(4), Core.listOf(9), Core.listOf(1)), Core.listOf(7, null, null, null, null)));
        console.log(viaIf(1) + " " + viaIf(-1) + " " + viaIf(-9));
        console.log(early() + " " + partly() + " " + steps);
    }
}
