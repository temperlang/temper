package big_lists;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class BigListsGlobal {
    private BigListsGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("big_lists"));
        int n = 200000;
        List<Integer> source = new ArrayList<>();
        {
            int i = 0;
            while (i < n) {
                source.add(i);
                i = i + 1;
            }
        }
        List<Integer> values = List.copyOf(source);
        List<Integer> b = new ArrayList<>();
        Core.listAddAll(b, values);
        Core.listAddAll(b, values, 0);
        b.add(n, -1);
        Core.listAddAll(b, values, n);
        console.log(b.size() + " " + Core.listGet(b, 0) + " " + Core.listGet(b, n - 1) + " " + Core.listGet(b, n) + " " + Core.listGet(b, 2 * n) + " " + Core.listGet(b, 2 * n + 1) + " " + Core.listGet(b, 3 * n));
        List<Integer> twice = new ArrayList<>();
        Core.listAddAll(twice, values);
        Core.listAddAll(twice, twice, 1);
        console.log(twice.size() + " " + Core.listGet(twice, 0) + " " + Core.listGet(twice, 1) + " " + Core.listGet(twice, n) + " " + Core.listGet(twice, n + 1));
        List<Integer> s = new ArrayList<>();
        s.add(7);
        s.add(8);
        List<Integer> removed = Core.listSplice(s, 1, 0, values);
        console.log(s.size() + " " + removed.size() + " " + Core.listGet(s, 0) + " " + Core.listGet(s, 1) + " " + Core.listGet(s, n) + " " + Core.listGet(s, n + 1));
        List<Integer> c = new ArrayList<>();
        c.add(7);
        c.add(8);
        Core.listSplice(c, -1, 0, values);
        Core.listSplice(c, 10 * n, 0, values);
        console.log(c.size() + " " + Core.listGet(c, 0) + " " + Core.listGet(c, n - 1) + " " + Core.listGet(c, n) + " " + Core.listGet(c, n + 1) + " " + Core.listGet(c, n + 2) + " " + Core.listGet(c, 2 * n + 1));
        List<Integer> own = new ArrayList<>();
        Core.listAddAll(own, values);
        List<Integer> gone = Core.listSplice(own, 0, 1, own);
        console.log(own.size() + " " + gone.size() + " " + Core.listGet(own, 0) + " " + Core.listGet(own, n - 1) + " " + Core.listGet(own, n) + " " + Core.listGet(own, 2 * n - 2));
        List<Integer> kept = Core.listFilterInt(values, x -> x != 5);
        console.log(kept.size() + " " + Core.listGet(kept, 4) + " " + Core.listGet(kept, 5) + " " + Core.listGet(kept, n - 2));
        List<Integer> codes = new ArrayList<>();
        {
            int i2 = 0;
            while (i2 < n) {
                codes.add(97 + (i2 % 3));
                i2 = i2 + 1;
            }
        }
        String text;
        try {
            text = Core.stringFromCodePoints(List.copyOf(codes));
        } catch (RuntimeException ignored) {
            text = "";
        }
        int four = Core.stringNext(text, Core.stringNext(text, Core.stringNext(text, Core.stringNext(text, 0))));
        console.log(Core.stringCountBetween(text, 0, text.length()) + " " + Core.stringSlice(text, 0, four));
    }
}
