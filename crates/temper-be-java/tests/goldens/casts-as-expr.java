package as_expr;

import java.util.logging.Logger;
import temper.core.Bubble;
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
            String t;
            try {
                t = Core.cast(Fuji.class, x).getName();
            } catch (Bubble ignored) {
                t = "Cast failed!";
            }
            console.log(t);
            @Nullable Object y = x;
            String t2;
            try {
                t2 = Core.cast(Apple.class, y).getName();
            } catch (Bubble ignored2) {
                t2 = "Cast from AnyValue failed!";
            }
            console.log(t2);
            String t3;
            try {
                t3 = Core.cast(Gala.class, y).getName();
            } catch (Bubble ignored3) {
                t3 = "Cast from AnyValue failed!";
            }
            console.log(t3);
            String t4;
            try {
                t4 = Core.cast(Fuji.class, y).getName();
            } catch (Bubble ignored4) {
                t4 = "Cast from AnyValue failed!";
            }
            console.log(t4);
            x = null;
            String t5;
            try {
                Core.requireNonNull(x);
                t5 = "Tasty nothing.";
            } catch (Bubble ignored5) {
                t5 = "Was null!";
            }
            console.log(t5);
        }
        thing0 = false;
        thing1 = 1;
        thing2 = "two";
        thing3 = 3.14;
    }
}
