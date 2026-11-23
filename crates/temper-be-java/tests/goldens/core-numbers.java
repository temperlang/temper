package core_numbers;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class CoreNumbersGlobal {
    private CoreNumbersGlobal() {}

    static final List<Integer> digits;

    static int from(int index) {
        return Core.listedReduceIntToInt(Core.listFrom(digits, index), 100, (s, n) -> s * 10 + n);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("core_numbers"));
        int one = 1;
        int big = 2147483647 * one;
        int small = -2147483648 * one;
        console.log((big + 1) + " " + (small - 1) + " " + (one + 1) + " " + (one - 1) + " " + (small + 1) + " " + (big - 1));
        long one64 = (long) one;
        long big64 = 9223372036854775807L * one64;
        long small64 = -big64 - one64;
        console.log(Long.toString(big64 + 1L) + " " + Long.toString(small64 - 1L) + " " + Long.toString(one64 - 1L));
        int zero = one - one;
        console.log(Integer.signum(big) + " " + Integer.signum(small) + " " + Integer.signum(zero) + " " + Integer.signum(one) + " " + Integer.signum(zero - one) + " " + Integer.signum(small + 1));
        digits = List.of(1, 2, 3);
        console.log(from(0) + " " + from(1) + " " + from(3) + " " + from(7));
        double inf = 1e308 * 10.0;
        double nan = inf - inf;
        double ninf = 0.0 - inf;
        console.log(Core.float64Near(nan, nan) + " " + Core.float64Near(inf, inf) + " " + Core.float64Near(ninf, ninf) + " " + Core.float64Near(nan, 1.0) + " " + Core.float64Near(1.0, nan));
        console.log(Core.float64Near(inf, 1.0) + " " + Core.float64Near(inf, ninf) + " " + Core.float64Near(inf, 1.0, 0.0, 0.0) + " " + Core.float64Near(inf, inf, 0.0, 0.0) + " " + Core.float64Near(inf, 1.0, 1.0, inf) + " " + Core.float64Near(1.0, ninf, 1.0, inf));
        console.log(Core.float64Near(1.0, 1.5, nan, 1.0) + " " + Core.float64Near(1.0, 1.5, 1.0, nan) + " " + Core.float64Near(1.0, 1.0, nan, nan) + " " + Core.float64Near(-0.0, 0.0, 0.0, 0.0));
        console.log(Core.float64Near(1.0, 1.0 + 1e-10) + " " + Core.float64Near(1.0, 1.1) + " " + Core.float64Near(1.0, 1.1, 0.2) + " " + Core.float64Near(0.0, 1e-300, 1e-9, 1e-12));
    }
}
