package angle_call;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class AngleCallGlobal {
    private AngleCallGlobal() {}

    static final Console console;

    static Class<?> whatever(int n) {
        if (n == 0) {
            console.log("0");
        }
        return Void.class;
    }

    static <T> T lastOf(List<T> things) {
        T thing = Core.listGet(things, things.size() - 1);
        return thing;
    }

    static {
        console = Core.getConsole(Logger.getLogger("angle_call"));
        whatever(1);
        List<String> someThings = List.<String>of("a", "b");
        String first = Classy.first(someThings);
        String second = new Classy(1).indexed(someThings);
        console.log("Some things: " + first + ", " + second);
        console.log("Picked: " + new Picker().pick(someThings));
    }
}
