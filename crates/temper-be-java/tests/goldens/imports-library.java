package imports_library;

import java.util.logging.Logger;
import modules.ModulesGlobal;
import modules.core.CoreGlobal;
import modules.core.Ratio;
import modules.helpers.HelpersGlobal;
import modules.state.Spot;
import renamed.Builtins;
import renamed.Checked;
import renamed.RenamedGlobal;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class ImportsLibraryGlobal {
    private ImportsLibraryGlobal() {}

    public static int both(int n) {
        return ModulesGlobal.sum(n) + HelpersGlobal.twice(n) + HelpersGlobal.base;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("imports_library"));
        console.log("both " + both(2));
        Ratio third = new Ratio(1, 3);
        String t = "ratio " + third.eq(new Ratio(3, 9)) + " ";
        @Nullable Ratio left = CoreGlobal.ratioOf(2, 6);
        String t2 = t + (left == null ? false : left.eq(third)) + " ";
        @Nullable Ratio left2 = CoreGlobal.ratioOf(1, 0);
        console.log(t2 + (left2 == null ? false : left2.eq(third)));
        Checked checked = new Checked(RenamedGlobal.lambda(RenamedGlobal.native_));
        console.log("renamed " + checked.delete() + " " + checked.getLocal() + " " + checked.end);
        Builtins b = new Builtins(4);
        console.log("builtins " + b.hash + " " + b.get(1) + " " + b.set(3) + " " + b.map(5) + " " + b.format() + " " + b.getInt() + " " + b.later());
        console.log(b.print());
        Spot spread = new Spot(3, 4);
        Spot far = new Spot(9, spread.y);
        console.log("spot far " + far.x + " " + far.y);
    }
}
