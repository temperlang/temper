package empty;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class EmptyGlobal {
    private EmptyGlobal() {}

    static final Console console;

    static {
        console = Core.getConsole(Logger.getLogger("empty"));
        Greeter greeter = new HiGreeter();
        Core.cast(HiGreeter.class, greeter).greet();
    }
}
