package reduce;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ReduceGlobal {
    private ReduceGlobal() {}

    static <T> boolean all(List<T> items, Predicate<T> check) {
        BiPredicate<Boolean, T> fn = (result, it) -> {
            if (result) {
                return check.test(it);
            } else {
                return false;
            }
        };
        return Core.listedReduceObjToBool(items, true, fn::test);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("reduce"));
        List<Integer> vals = List.<Integer>of(2, 3, 4);
        int sum = Core.listedReduceInt(vals, (sum2, n) -> sum2 + n);
        String poorReverseJoin = Core.listedReduceIntToObj(vals, "", (text, n) -> n + text);
        console.log("Reductions: " + sum + " " + poorReverseJoin);
        console.log("All even: " + all(vals, n -> n % 2 == 0));
        console.log("All positive: " + all(vals, n -> n > 0));
    }
}
