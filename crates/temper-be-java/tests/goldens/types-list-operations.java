package operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class OperationsGlobal {
    private OperationsGlobal() {}

    static final Console console;

    static <T> void reportLength(List<T> list) {
        console.log("Length: " + list.size());
    }

    static void reportSplit(String string, String sep) {
        List<String> strings = Core.stringSplit(string, sep);
        String len = Integer.toString(strings.size());
        console.log("Split " + Core.listJoinObj(strings, ", ", x -> x) + " - " + len);
    }

    static void something(Consumer<List<String>> callback) {
        callback.accept(List.of("Called", "with", "listed."));
    }

    static void runSomething() {
        something(words -> console.log(Core.listJoinObj(words, " ", word -> word)));
    }

    /** Compares strings by code point, as Temper does: -1, 0, or 1. */
    private static int compareCodePoints(String a, String b) {
        int length = Math.min(a.length(), b.length());
        for (int i = 0; i < length; i++) {
            char x = a.charAt(i);
            char y = b.charAt(i);
            if (x != y) {
                boolean surrogate = Character.isSurrogate(x);
                if (surrogate != Character.isSurrogate(y)) {
                    return surrogate ? 1 : -1;
                }
                return x < y ? -1 : 1;
            }
        }
        return Integer.signum(a.length() - b.length());
    }

    static {
        console = Core.getConsole(Logger.getLogger("operations"));
        reportLength(List.of(4, 5, 6));
        List<Integer> ls = Core.listSortedInt(List.of(5, 6, 7, 8, 9, 0, 1, 2, 3, 4), (i, j) -> i - j);
        console.log("All  " + Core.listJoinInt(ls, ", ", x -> Integer.toString(x)));
        console.log("Even " + Core.listJoinInt(Core.listFilterInt(ls, x -> (x & 1) == 0), ", ", x -> Integer.toString(x)));
        console.log("Odd  " + Core.listJoinInt(Core.listFilterInt(ls, x -> (x & 1) != 0), ", ", x -> Integer.toString(x)));
        console.log("Neg  " + Core.listJoinInt(Core.listMapIntToInt(ls, x -> -x), ", ", x -> Integer.toString(x)));
        List<Integer> listed = ls;
        console.log("Neg  " + Core.listJoinInt(Core.listMapIntToInt(listed, x -> -x), ", ", x -> Integer.toString(x)));
        List<Integer> listedList = List.copyOf(listed);
        console.log("Listed list length is " + listedList.size());
        console.log("Looping");
        int n = ls.size();
        {
            int i = 0;
            while (i < n) {
                console.log("  " + Core.listGet(ls, i));
                i = i + 1;
            }
        }
        reportSplit("a ; b ; c", " ; ");
        reportSplit("def", "");
        reportSplit(" g  h i", " ");
        reportSplit(" ", " ");
        reportSplit("", " ");
        reportSplit("", "");
        reportSplit("🌍", "");
        Function<List<String>, List<String>> fn11 = list -> {
            List<String> builder = new ArrayList<>(list);
            Core.listSort(builder, (a, b) -> compareCodePoints(a, b));
            return builder;
        };
        List<List<String>> triangleOfFooBar = new ArrayList<>(Core.listMapObjToObj(List.of(List.of(), List.of("bar"), List.of("bar", "baz"), List.of("bar", "baz", "boo"), List.of("bar", "baz", "boo", "far"), List.of("bar", "baz", "boo", "far", "faz"), List.of("far", "faz", "foo", "bar", "baz", "boo")), fn11));
        {
            int j = 0;
            while (j < triangleOfFooBar.size()) {
                List<String> row = Core.listGet(triangleOfFooBar, j);
                console.log("Fwd  [" + Core.listJoinObj(row, " ", x -> x) + "]");
                Collections.reverse(row);
                j = j + 1;
            }
        }
        Collections.reverse(triangleOfFooBar);
        {
            int j2 = 0;
            while (j2 < triangleOfFooBar.size()) {
                List<String> row2 = Core.listGet(triangleOfFooBar, j2);
                console.log("Back [" + Core.listJoinObj(row2, " ", x -> x) + "]");
                j2 = j2 + 1;
            }
        }
        List<String> messageBuilder = new ArrayList<>();
        messageBuilder.add("hello");
        Core.listSet(messageBuilder, 0, "hi");
        Core.listAddAll(messageBuilder, List.of("there", "y'all"));
        Core.listAdd(messageBuilder, "say", 0);
        List<String> copyThat = new ArrayList<>(messageBuilder);
        Core.listAddAll(messageBuilder, List.of("more", "than"), 1);
        Core.listAddAll(copyThat, messageBuilder);
        console.log(Core.listJoinObj(messageBuilder, " ", x -> x));
        console.log(Core.listJoinObj(Core.listSorted(copyThat, (a, b) -> compareCodePoints(b, a)), " ", x -> x));
        console.log(Core.listJoinObj(copyThat, " ", x -> x));
        String oldLast = Core.listRemoveLast(copyThat);
        console.log("Removed " + oldLast + " so " + Core.listJoinObj(copyThat, " ", x -> x));
        List<String> nextLast = Core.listSplice(copyThat, copyThat.size() - 1, 1);
        List<String> oldWords = Core.listSplice(copyThat, 4, 3, nextLast);
        Core.listSplice(copyThat, null, 0, oldWords);
        Core.listSplice(copyThat, copyThat.size(), null, List.of("and", "the", "end"));
        console.log(Core.listJoinObj(copyThat, " ", x -> x));
        Core.listSplice(copyThat, null, null, List.of("gone"));
        console.log(Core.listJoinObj(copyThat, " ", x -> x));
        List<String> pastEnd = Core.listSplice(copyThat, copyThat.size() + 5, 1);
        Core.listSplice(copyThat, -3, 0, List.of("first"));
        console.log(pastEnd.size() + " " + Core.listJoinObj(copyThat, " ", x -> x));
        messageBuilder.clear();
        messageBuilder.add("Bye");
        console.log(Core.listJoinObj(messageBuilder, " ", x -> x));
        {
            int i2 = -2;
            while (i2 <= 10) {
                console.log("Slice(" + i2 + ", " + (i2 + 2) + "): [" + Core.listJoinInt(Core.listSlice(ls, i2, i2 + 2), ", ", x -> Integer.toString(x)) + "]");
                i2 = i2 + 1;
            }
        }
        console.log("Outside fallback: " + Core.listGetOr(ls, ls.size(), 123));
        {
            Function<Boolean, String> booleanToString = b -> Boolean.toString(b);
            List<Boolean> ls2 = List.of(true, false);
            String joined = Core.listJoinBool(ls2, ", ", booleanToString);
            List<Boolean> inverted = Core.listMapObjToBool(ls2, b -> !b);
            Function<Boolean, String> fn23 = b -> {
                if (b) {
                    return "not false";
                } else {
                    return "not true";
                }
            };
            String joinedAndInverted = Core.listJoinBool(inverted, ", ", fn23);
            console.log("list = [" + joined + "]");
            console.log("list (each inverted) = [" + joinedAndInverted + "]");
            List<Boolean> onlyTrue = Core.listFilterBool(ls2, b -> b);
            List<Boolean> onlyFalse = Core.listFilterBool(ls2, b -> !b);
            console.log("list (== true only) = [" + Core.listJoinBool(onlyTrue, ", ", booleanToString) + "]");
            console.log("list (== false only) = [" + Core.listJoinBool(onlyFalse, ", ", booleanToString) + "]");
        }
        {
            List<Double> sizes = List.of(0.25, 3.0);
            DoubleFunction<String> fn12 = f -> {
                if (Double.compare(f, 1.0) < 0) {
                    return "small";
                } else {
                    return "big";
                }
            };
            List<String> labels = Core.listMapDoubleToObj(sizes, fn12);
            console.log("sizes = [" + Core.listJoinObj(labels, ", ", s -> s) + "]");
        }
        runSomething();
        {
            List<String> aListBuilder = new ArrayList<>();
            aListBuilder.add("first");
            List<String> builtFirst = List.copyOf(aListBuilder);
            aListBuilder.add("second");
            List<String> builtSecond = List.copyOf(aListBuilder);
            aListBuilder.add("third");
            console.log("builtFirst has " + Core.listJoinObj(builtFirst, ", ", s -> s));
            console.log("builtSecond has " + Core.listJoinObj(builtSecond, ", ", s -> s));
        }
    }
}
