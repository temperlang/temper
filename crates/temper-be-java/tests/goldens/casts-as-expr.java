package as_expr;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class AsExprGlobal {
    private AsExprGlobal() {}

    public static final Object thing0;
    public static final Object thing1;
    public static final Object thing2;
    public static final Object thing3;

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
        thing0 = false;
        thing1 = 1;
        thing2 = "two";
        thing3 = 3.14;
    }
}
