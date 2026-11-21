package locals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.logging.Logger;
import locals.function.Int32ListBuilderProcedure;
import temper.core.Core;
import temper.core.Core.Console;

public final class LocalsGlobal {
    private LocalsGlobal() {}

    static final Console console;

    static String show(List<Integer> x) {
        List<String> s = new ArrayList<>();
        s.add("[");
        String c = "";
        {
            int i = 0;
            while (i < x.size()) {
                s.add(c);
                s.add(Integer.toString(Core.listGet(x, i)));
                c = ", ";
                i = i + 1;
            }
        }
        s.add("]");
        return Core.listJoinObj(s, "", v -> v);
    }

    static <T> T nestBlock(Supplier<T> b) {
        return b.get();
    }

    static void nestBlockVoid(Runnable b) {
        b.run();
    }

    static void constantCapture(List<Integer> x, int a) {
        int b = a * 1;
        List<Integer> out = Core.listMapIntToInt(x, n -> n + b);
        console.log("constantCapture = " + show(out));
    }

    static void constantCaptureNested(List<Integer> x, int a) {
        int b = a * 1;
        List<Integer> out = nestBlock(() -> Core.listMapIntToInt(x, n -> n + b));
        console.log("constantCaptureNested = " + show(out));
    }

    static void mutableCapture(List<Integer> x) {
        class SumCell {
            int value;
        }
        final SumCell sum = new SumCell();
        sum.value = 0;
        IntUnaryOperator fn = n -> {
            sum.value = sum.value + n;
            return sum.value;
        };
        List<Integer> out = Core.listMapIntToInt(x, fn);
        console.log("mutableCapture = " + show(out) + "; sum=" + sum.value);
    }

    static void mutableCaptureNested(List<Integer> x) {
        class SumCell {
            int value;
        }
        final SumCell sum = new SumCell();
        sum.value = 0;
        Supplier<List<Integer>> fn = () -> {
            IntUnaryOperator fn2 = n -> {
                sum.value = sum.value + n;
                return sum.value;
            };
            return Core.listMapIntToInt(x, fn2);
        };
        List<Integer> out = nestBlock(fn);
        console.log("mutableCaptureNested = " + show(out) + "; sum=" + sum.value);
    }

    public static void thresh(List<Integer> values, int value) {
        class Value2Cell {
            int value;
        }
        final Value2Cell value2 = new Value2Cell();
        value2.value = value;
        value2.value = value2.value + 1;
        List<Integer> result = Core.listFilterInt(values, it -> it > value2.value);
        console.log("thresh length = " + result.size());
    }

    public static void thresh2(List<Integer> values, int value) {
        class Value2Cell {
            int value;
        }
        final Value2Cell value2 = new Value2Cell();
        value2.value = value;
        value2.value = value2.value + 1;
        List<Integer> result = Core.listFilterInt(values, it -> it > value2.value);
        console.log("thresh2 length = " + result.size());
    }

    static void selfRecursion(List<Integer> x) {
        class MapperGroup {
            void mapper(int i, List<Integer> out) {
                if (i < x.size()) {
                    out.add(Core.listGet(x, i) * 2);
                    mapper(i + 1, out);
                }
            }
        }
        MapperGroup mapperGroup = new MapperGroup();
        Int32ListBuilderProcedure mapper = mapperGroup::mapper;
        List<Integer> out2 = new ArrayList<>();
        mapper.accept(0, out2);
        console.log("selfRecursion = " + show(out2));
    }

    static void selfRecursionNested(List<Integer> x) {
        class MapperGroup {
            void mapper(int i, List<Integer> out) {
                Runnable fn2 = () -> {
                    if (i < x.size()) {
                        out.add(Core.listGet(x, i) * 2);
                        mapper(i + 1, out);
                    }
                };
                nestBlockVoid(fn2);
            }
        }
        MapperGroup mapperGroup = new MapperGroup();
        Int32ListBuilderProcedure mapper = mapperGroup::mapper;
        List<Integer> out2 = new ArrayList<>();
        mapper.accept(0, out2);
        console.log("selfRecursionNested = " + show(out2));
    }

    static void mutualRecursion(List<Integer> x) {
        class WalkerGroup {
            void walker(int i, List<Integer> out) {
                if (i < x.size()) {
                    putter(i, out);
                }
            }

            void putter(int i2, List<Integer> out2) {
                out2.add(Core.listGet(x, i2) * 2);
                walker(i2 + 1, out2);
            }
        }
        WalkerGroup walkerGroup = new WalkerGroup();
        Int32ListBuilderProcedure walker = walkerGroup::walker;
        List<Integer> out3 = new ArrayList<>();
        walker.accept(0, out3);
        console.log("mutualRecursion = " + show(out3));
    }

    static List<Integer> gatherHandlers(Hub<Integer> hub) {
        List<Integer> items = new ArrayList<>();
        {
            int i = 0;
            while (i < 3) {
                int i2 = i;
                hub.onAction(item -> items.add(item + i2));
                i = i + 1;
            }
        }
        return items;
    }

    static {
        console = Core.getConsole(Logger.getLogger("locals"));
        constantCapture(List.<Integer>of(1, 2, 3), 1);
        constantCaptureNested(List.<Integer>of(1, 2, 3), 1);
        mutableCapture(List.<Integer>of(1, 2, 3));
        mutableCaptureNested(List.<Integer>of(1, 2, 3));
        thresh(List.<Integer>of(4, 5, 6), 4);
        thresh2(List.<Integer>of(4, 5, 6), 4);
        selfRecursion(List.<Integer>of(1, 2, 3));
        selfRecursionNested(List.<Integer>of(1, 2, 3));
        mutualRecursion(List.<Integer>of(1, 2, 3));
        Hub<Integer> hub = new Hub<Integer>();
        List<Integer> items = gatherHandlers(hub);
        hub.actionPerformed(1);
    }
}
