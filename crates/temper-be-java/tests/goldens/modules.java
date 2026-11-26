package modules;

import java.util.logging.Logger;
import modules.core.CoreGlobal;
import modules.core.Ratio;
import modules.helpers.HelpersGlobal;
import modules.helpers.deep.DeepGlobal;
import modules.machine.MachineGlobal;
import modules.state.Spot;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class ModulesGlobal {
    private ModulesGlobal() {}

    static final int base2;

    public static int sum(int n) {
        return HelpersGlobal.twice(n) + HelpersGlobal.base + base2;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("modules"));
        int calls2 = HelpersGlobal.calls;
        base2 = 1;
        console.log(DeepGlobal.greet("modules"));
        console.log("sum " + sum(3));
        console.log("share " + CoreGlobal.share(7, 2) + " " + HelpersGlobal.intDoubled(21));
        console.log(new Engine("own").name + ", " + MachineGlobal.describe());
        HelpersGlobal.bump();
        HelpersGlobal.bump();
        console.log("calls " + calls2);
        console.log("calls now " + HelpersGlobal.bump());
        Ratio half = new Ratio(1, 2);
        String t = half.eq(new Ratio(2, 4)) + " ";
        @Nullable Ratio left = CoreGlobal.ratioOf(3, 6);
        String t2 = t + (left == null ? false : left.eq(half)) + " ";
        Ratio left2 = half;
        @Nullable Ratio right = CoreGlobal.ratioOf(1, 0);
        console.log(t2 + !(right == null ? false : left2.eq(right)));
        Spot spread = new Spot(1, 2);
        Spot moved = new Spot(spread.x, 5);
        console.log("spot " + moved.x + " " + moved.y);
    }
}
