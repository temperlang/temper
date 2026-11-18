package private_method;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PrivateMethodGlobal {
    private PrivateMethodGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("private_method"));
        C c = new C();
        console.log("method1(10)=" + c.method1(10));
        console.log("method2(10)=" + c.method2(10));
        Money a = new Money(150);
        Money b = new Money(175);
        console.log("plus: " + a.plus(b).toString());
        console.log("same whole: " + a.sameWhole(b));
        console.log("same rounded: " + a.sameRounded(b));
        a.spendFrom(b);
        a.spendFrom(b);
        console.log("b spent: " + b.getTimesSpent());
        Label label = new Label(1);
        console.log("close: " + label.close(new TenSink()));
        console.log("noted: " + label.noted("!"));
        Tally leaf = new Tally(2, null, List.of());
        Tally mid = new Tally(3, leaf, List.of(leaf, leaf));
        Tally top = new Tally(1, mid, List.of(mid, leaf));
        console.log("depths: " + leaf.depth + " " + mid.depth + " " + top.depth);
        console.log("totals: " + leaf.getTotal() + " " + mid.getTotal() + " " + top.getTotal());
    }
}
