package minimal_repro;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class MinimalReproGlobal {
    private MinimalReproGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("minimal_repro"));
        List<Integer> ls = List.<Integer>of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        console.log("All  " + Core.listJoinInt(ls, ", ", x -> Integer.toString(x)));
        console.log("Even " + Core.listJoinInt(Core.listFilterInt(ls, x -> (x & 1) == 0), ", ", x -> Integer.toString(x)));
        console.log("Odd  " + Core.listJoinInt(Core.listFilterInt(ls, x -> (x & 1) != 0), ", ", x -> Integer.toString(x)));
        console.log("Neg  " + Core.listJoinInt(Core.listMapIntToInt(ls, x -> -x), ", ", x -> Integer.toString(x)));
        List<String> strs = Core.stringSplit("1 ; 2 ; 3", " ; ");
        console.log("Splt " + Core.listJoinObj(strs, ", ", x -> x));
    }
}
