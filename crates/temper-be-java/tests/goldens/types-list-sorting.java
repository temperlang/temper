package sorting;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import sorting.function.Float64Float64Int32;
import temper.core.Core;
import temper.core.Core.Console;

public final class SortingGlobal {
    private SortingGlobal() {}

    static final Console console;

    static void mungeInts(List<Integer> ints) {
        List<Integer> builder = new ArrayList<>(ints);
        Core.listSortInt(builder, (a, b) -> a - b);
        console.log(Core.listJoinInt(Core.listSortedInt(ints, (a2, b2) -> a2 - b2), ", ", a3 -> Integer.toString(a3)));
        console.log(Core.listJoinInt(builder, ", ", a4 -> Integer.toString(a4)));
    }

    static void mungeFloats(List<Double> floats) {
        List<Double> builder = new ArrayList<>(floats);
        Float64Float64Int32 compareFloats = (a, b) -> (int) Math.signum(a - b);
        Core.listSort(builder, compareFloats::applyAsInt);
        console.log(Core.listJoinDouble(Core.listSorted(floats, compareFloats::applyAsInt), ", ", a2 -> Core.float64ToString(a2)));
        console.log(Core.listJoinDouble(builder, ", ", a3 -> Core.float64ToString(a3)));
    }

    static {
        console = Core.getConsole(Logger.getLogger("sorting"));
        List<Entry> entries = new ArrayList<>(List.<Entry>of(new Entry(0, 0), new Entry(1, 1), new Entry(2, 2), new Entry(3, 0), new Entry(4, 1), new Entry(5, 2), new Entry(6, 0), new Entry(7, 1), new Entry(8, 2)));
        List<Entry> sortedEntries = Core.listSorted(entries, (a, b) -> a.group - b.group);
        console.log(Core.listJoinObj(sortedEntries, "\n", a -> a.toString()));
        console.log(Core.listGet(entries, 1).toString());
        Core.listSort(entries, (a, b) -> a.group - b.group);
        console.log(Core.listJoinObj(entries, "\n", a -> a.toString()));
        mungeInts(List.<Integer>of(2, 1));
        mungeFloats(List.<Double>of(3.4, 1.2));
    }
}
