package java_generics;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class JavaGenericsGlobal {
    private JavaGenericsGlobal() {}

    static <T> T id(T x) {
        return x;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("java_generics"));
        List<Integer> a = List.<Integer>of(1000, 2);
        List<Integer> b = List.<Integer>of(1000, 3);
        console.log(Boolean.toString(Core.listGet(a, 0).intValue() == Core.listGet(b, 0)));
        console.log(Boolean.toString(Core.listGet(a, 1).intValue() != Core.listGet(b, 1)));
        console.log(Boolean.toString(id(1000).intValue() == id(1000)));
        List<@Nullable Integer> maybe = Core.<@Nullable Integer>listOf(1, null);
        @Nullable Integer subject = Core.listGet(maybe, 1);
        console.log(Integer.toString(subject == null ? -1 : subject));
        List<@Nullable Integer> builder = new ArrayList<>();
        builder.add(null);
        console.log(Integer.toString(Core.listCopyOf(builder).size()));
    }
}
