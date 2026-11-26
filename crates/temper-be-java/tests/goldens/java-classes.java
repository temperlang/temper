package java_classes;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class JavaClassesGlobal {
    private JavaClassesGlobal() {}

    static final String greeting;

    static void initStatics() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("java_classes"));
        greeting = "hi";
        Person.banner = greeting + "!";
        Person p = new Person("Ann", true);
        p.setVolume(7);
        console.log(p.greet(2));
        console.log(p.loud + " " + p.level + " " + Person.banner + " " + Person.getTwice() + " " + Named.zero);
        Named n = p;
        console.log(n.greet(1));
    }
}
