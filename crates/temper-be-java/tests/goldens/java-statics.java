package java_statics;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class JavaStaticsGlobal {
    private JavaStaticsGlobal() {}

    public static String describe(@Nullable Policy policy) {
        Policy policy2 = policy != null ? policy : Policy.standard;
        return "[" + policy2.names + "]";
    }

    public static String describe() {
        return describe(null);
    }

    static void initStatics() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("java_statics"));
        String allowed = "a b";
        Policy.standard = new Policy(allowed);
        Policy.strict = allowed.isEmpty();
        console.log(describe() + " " + Policy.strict);
        console.log(Policy.getTextOnly().names + "|" + Policy.getLimit() + "|" + Policy.getLabel() + "|" + Policy.isOn() + "|" + Policy.peek());
    }
}
