package functions;

import functions.exporter.ExporterGlobal;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class FunctionsGlobal {
    private FunctionsGlobal() {}

    public static double norm(double x, double y) {
        return Math.sqrt(ExporterGlobal.normSquared(x, y));
    }

    public static double something(List<Double> vals) {
        return Core.listedReduceDoubleToDouble(vals, 0.0, ExporterGlobal::normSquared);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("functions"));
        double a = 3.0;
        a = a;
        double b = a + 1.0;
        String values = Core.listJoinDouble(List.<Double>of(a, b, norm(a, b)), ", ", it -> Core.float64ToString(it));
        console.log(values + " triangle");
        console.log(Core.float64ToString(something(List.<Double>of(a, b))));
    }
}
