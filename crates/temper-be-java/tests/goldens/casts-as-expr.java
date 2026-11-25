package as_expr;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class AsExprGlobal {
    private AsExprGlobal() {}

    public static final Object thing2;
    public static final Object thing1;
    public static final Object thing0;
    public static final Object thing3;

    static String describe(@Nullable Object v) {
        if (v != null && v instanceof Fuji) {
            return "Fuji " + ((Fuji) v).getName();
        }
        if (v != null && v instanceof Apple) {
            return ((Apple) v).getName();
        } else {
            return "other";
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("as_expr"));
        {
            @Nullable Apple x;
            x = new Fuji();
            console.log(Core.requireNonNull(x).getName());
            console.log(Core.cast(Fuji.class, x).getName());
            x = new Gala();
            console.log(x != null && x instanceof Fuji ? Core.cast(Fuji.class, x).getName() : "Cast failed!");
            @Nullable Object y = x;
            console.log(Core.cast(Apple.class, y).getName());
            console.log(Core.cast(Gala.class, y).getName());
            x = null;
            console.log(x != null ? "Tasty nothing." : "Was null!");
        }
        thing2 = "two";
        thing1 = 1;
        console.log(describe(new Fuji()));
        console.log(describe(new Gala()));
        console.log(describe(thing2));
        console.log(describe(thing1));
        console.log(describe(null));
        thing0 = false;
        thing3 = 3.14;
    }
}
