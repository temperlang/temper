package call_override_from_subtype;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class CallOverrideFromSubtypeGlobal {
    private CallOverrideFromSubtypeGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("call_override_from_subtype"));
        Slippy slippy = new Slippy();
        Slappy slappy = slippy;
        console.log(Integer.toString(slippy.getThingums()));
        console.log(Integer.toString(slappy.getThingums()));
        console.log(Integer.toString(slippy.getThingumses()));
        console.log(Boolean.toString(slippy.checkOverridden()));
        console.log(Boolean.toString(slippy.checkOwn()));
        console.log(new D().a());
        console.log(new D().getThing());
        console.log(new DSplit().a());
        console.log(new DDeep().a());
        console.log(new EDeep().a());
    }
}
