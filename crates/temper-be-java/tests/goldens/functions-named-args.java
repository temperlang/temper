package named_args;

import java.util.Optional;
import java.util.function.IntFunction;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class NamedArgsGlobal {
    private NamedArgsGlobal() {}

    static final Console console;
    static volatile String message;
    static volatile boolean repeat;

    static String changeRepeat() {
        repeat = true;
        return message;
    }

    static boolean changeMessage() {
        message = "Namaste!";
        return repeat;
    }

    static <T> T calc(int n, IntFunction<T> transform, @Nullable CalcOptions options) {
        CalcOptions options2 = options != null ? options : new CalcOptions();
        int origin = options2.origin;
        int scale = options2.scale;
        return transform.apply((n - origin) * scale);
    }

    static <T> T calc(int n, IntFunction<T> transform) {
        return calc(n, transform, null);
    }

    static {
        console = Core.getConsole(Logger.getLogger("named_args"));
        new Greet();
        new Greet("Bye!");
        new Greet("Hola!", true);
        new Greet("Salut!");
        new Greet(null, true);
        new Greet(null, true);
        {
            boolean arg = true;
            new Greet("Ni hao!", arg);
        }
        message = "Hi!";
        repeat = false;
        {
            boolean arg2 = changeMessage();
            String arg3 = changeRepeat();
            new Greet(arg3, arg2);
        }
        console.log(new Person().toString());
        console.log(new Person("Bob").toString());
        console.log(new Person("Carrie", 50).toString());
        new Person("Dan").greet("Hallo");
        console.log(calc(2, n -> Integer.toString(n)));
        console.log(calc(2, n -> Integer.toString(n), new CalcOptions(3, 4)));
        console.log(calc(2, n -> Integer.toString(n), new CalcOptions(3)));
        console.log(calc(2, n -> Integer.toString(n), new CalcOptions(null, 4)));
        console.log(calc(2, n -> "<" + n + ">", new CalcOptions(5)));
        IntFunction<Optional<?>> fn6 = n -> {
            console.log(Integer.toString(n));
            return Optional.empty();
        };
        calc(2, fn6, new CalcOptions(-2));
    }
}
