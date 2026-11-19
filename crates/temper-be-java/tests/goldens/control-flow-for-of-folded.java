package for_of_folded;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ForOfFoldedGlobal {
    private ForOfFoldedGlobal() {}

    static int total(List<Integer> xs) {
        int t = 0;
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                int x = Core.listGet(xs, i);
                i = i + 1;
                t = t + x;
            }
        }
        return t;
    }

    static int totalForEach(List<Integer> xs) {
        int t = 0;
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                int x = Core.listGet(xs, i);
                i = i + 1;
                t = t + x;
            }
        }
        return t;
    }

    static int totalFiltered(List<Integer> xs) {
        class TCell {
            int value;
        }
        final TCell t = new TCell();
        t.value = 0;
        IntPredicate fn = x -> {
            t.value = t.value + x;
            return true;
        };
        Core.listFilterInt(xs, fn);
        return t.value;
    }

    static int totalNested(List<List<Integer>> xss) {
        int t = 0;
        {
            int length = xss.size();
            int i = 0;
            while (i < length) {
                List<Integer> xs = Core.listGet(xss, i);
                i = i + 1;
                {
                    int length2 = xs.size();
                    int i2 = 0;
                    while (i2 < length2) {
                        int x = Core.listGet(xs, i2);
                        i2 = i2 + 1;
                        t = t + x;
                    }
                }
            }
        }
        return t;
    }

    static int firstOver(List<Integer> xs, int n) {
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                int x = Core.listGet(xs, i);
                i = i + 1;
                if (x > n) {
                    return x;
                }
            }
        }
        return -1;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("for_of_folded"));
        console.log("total " + total(List.<Integer>of(1, 2, 3)));
        console.log("forEach " + totalForEach(List.<Integer>of(1, 2, 3)));
        console.log("filter " + totalFiltered(List.<Integer>of(1, 2, 3)));
        console.log("nested " + totalNested(List.<List<Integer>>of(List.<Integer>of(1, 2), List.<Integer>of(3), List.<Integer>of())));
        console.log("firstOver " + firstOver(List.<Integer>of(1, 2, 3), 1) + " " + firstOver(List.<Integer>of(1, 2, 3), 5));
    }
}
