package ops;

import java.util.List;
import java.util.function.DoubleFunction;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class OpsGlobal {
    private OpsGlobal() {}

    static final Console console;
    static final double two;

    static void near(String label, double actual, double expected) {
        boolean subject = Core.float64Near(actual, expected);
        String result = subject == true ? "✅" : "❌ wanted " + Core.float64ToString(expected) + ", was " + Core.float64ToString(actual);
        console.log(label + ": " + result);
    }

    static {
        console = Core.getConsole(Logger.getLogger("ops"));
        double one = 1.0;
        one = one;
        two = one + one;
        double half = one / two;
        near("half.abs()", Math.abs(half), half);
        near("(-half).abs()", Math.abs(-half), half);
        double three = one + two;
        near("half.acos()", Math.acos(half), Math.PI / three);
        double pi = Math.PI;
        double six = two * three;
        near("half.asin()", Math.asin(half), pi / six);
        double four = two * two;
        near("(-one).atan()", Math.atan(-one), -pi / four);
        near("one.atan2(-one)", Math.atan2(one, -one), (three * pi) / four);
        near("(four / three).ceil()", Math.ceil(four / three), two);
        near("(-four / three).ceil()", Math.ceil(-four / three), -one);
        near("(pi / three).cos()", Math.cos(pi / three), half);
        double e = Math.E;
        near("one.cosh()", Math.cosh(one), (e + one / e) / two);
        near("half.exp() == e.sqrt()", Math.exp(half), Math.sqrt(e));
        near("half.expm1()", Math.expm1(half), Math.sqrt(e) - one);
        near("(four / three).floor()", Math.floor(four / three), one);
        near("(-four / three).floor()", Math.floor(-four / three), -two);
        near("e.log()", Math.log(Math.E), one);
        double five = one + four;
        double ten = two * five;
        near("ten.log10()", Math.log10(ten), one);
        near("(e - one).log1p()", Math.log1p(e - one), one);
        DoubleFunction<String> fn = x -> {
            DoubleFunction<String> fn2 = y -> {
                String pair = Core.listJoinDouble(List.<Double>of(Math.max(x, y), Math.min(x, y)), ", ", z -> Core.float64ToString(z));
                return "(" + pair + ")";
            };
            return Core.listJoinDouble(List.<Double>of(Double.NaN, two), ", ", fn2);
        };
        String maxMinCombos = Core.listJoinDouble(List.<Double>of(Double.NaN, one), ", ", fn);
        console.log("maxMinCombos: " + maxMinCombos);
        double quarter = half / two;
        near("2.25 % 2.0", (two + quarter) % two, 0.25);
        near("2.25 % -2.0", (two + quarter) % -two, 0.25);
        near("-2.25 % 2.0", -(two + quarter) % two, -0.25);
        near("-2.25 % -2.0", -(two + quarter) % -two, -0.25);
        near("pi.round()", Math.round(pi), three);
        near("(-pi).round()", Math.round(-pi), -three);
        near("pi.sign()", Math.signum(pi), one);
        near("(-pi).sign()", Math.signum(-pi), -one);
        double zero = one - one;
        console.log("zero.sign(): " + Core.float64ToString(Math.signum(zero)));
        console.log("(-zero).sign(): " + Core.float64ToString(Math.signum(-zero)));
        near("(pi / six).sin()", Math.sin(pi / six), half);
        near("one.sinh()", Math.sinh(one), (e - one / e) / two);
        near("(-pi / four).tan()", Math.tan(-pi / four), -one);
        near("(three * pi / four).tan()", Math.tan((three * pi) / four), -one);
        near("one.tanh()", Math.tanh(one), (Math.pow(e, 2.0) - 1.0) / (Math.pow(e, 2.0) + 1.0));
        console.log("one.near(one + 0.1): " + Core.float64Near(one, one + 0.1));
        console.log("one.near(one + 0.25, absTol = 0.25): " + Core.float64Near(one, one + 0.25, null, 0.25));
        console.log("one.near(one + 0.1 + 1e-9, absTol = 0.1): " + Core.float64Near(one, one + 0.1 + 1e-9, null, 0.1));
        console.log("one.near(one - 0.1, absTol = 0.11): " + Core.float64Near(one, one - 0.1, null, 0.11));
        console.log("ten.near(ten + 0.1, absTol = 0.011): " + Core.float64Near(ten, ten + 0.1, null, 0.011));
        console.log("ten.near(ten + 0.1, relTol = 0.011): " + Core.float64Near(ten, ten + 0.1, 0.011));
    }
}
