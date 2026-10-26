package temper.std.temporal;
import java.util.List;
import temper.core.Core;
import java.time.LocalDate;
import temper.std.json.JsonString;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;
import temper.std.json.InterchangeContext;
public final class TemporalGlobal {
    private TemporalGlobal() {
    }
    static final List<Integer> daysInMonth__33;
    static final List<Integer> dayOfWeekLookupTableLeapy__34;
    static final List<Integer> dayOfWeekLookupTableNotLeapy__35;
    static void encodeToJson(LocalDate this__11, JsonProducer p__92) {
        p__92.stringValue(this__11.toString());
    }
    static LocalDate decodeFromJson(JsonSyntaxTree t__95, InterchangeContext ic__96) {
        JsonString t_132 = Core.cast(JsonString.class, t__95);
        return LocalDate.parse(t_132.getContent());
    }
    static JsonAdapter<LocalDate> jsonAdapter() {
        return new DateJsonAdapter();
    }
    static boolean isLeapYear(int year__40) {
        if (year__40 % 4 == 0) {
            if (year__40 % 100 != 0) {
                return true;
            } else {
                return year__40 % 400 == 0;
            }
        } else {
            return false;
        }
    }
    /**
     * If the decimal representation of &#92;|num&#92;| is longer than [minWidth],
     * then appends that representation.
     * Otherwise any sign for [num] followed by enough zeroes to bring the
     * whole length up to [minWidth].
     *
     * ```temper
     * // When the width is greater than the decimal's length,
     * // we pad to that width.
     * "0123" == do &#123;
     *   let sb = new StringBuilder();
     *   padTo(4, 123, sb);
     *   sb.toString()
     * &#125;
     *
     * // When the width is the same or lesser, we just use the string form.
     * "123" == do &#123;
     *   let sb = new StringBuilder();
     *   padTo(2, 123, sb);
     *   sb.toString()
     * &#125;
     *
     * // The sign is always on the left.
     * "-01" == do &#123;
     *   let sb = new StringBuilder();
     *   padTo(3, -1, sb);
     *   sb.toString()
     * &#125;
     * ```
     */
    static void padTo(int minWidth__42, int num__43, StringBuilder sb__44) {
        String decimal__46 = Integer.toString(num__43, 10);
        int decimalIndex__47 = 0;
        int decimalEnd__48 = decimal__46.length();
        boolean t_148;
        if (decimalIndex__47 < decimalEnd__48) {
            t_148 = decimal__46.codePointAt(decimalIndex__47) == 45;
        } else {
            t_148 = false;
        }
        if (t_148) {
            sb__44.append("-");
            decimalIndex__47 = Core.stringNext(decimal__46, decimalIndex__47);
        }
        int nNeeded__49 = minWidth__42 - Core.stringCountBetween(decimal__46, decimalIndex__47, decimalEnd__48);
        while (nNeeded__49 > 0) {
            sb__44.append("0");
            nNeeded__49 = nNeeded__49 - 1;
        }
        Core.stringBuilderAppendBetween(sb__44, decimal__46, decimalIndex__47, decimalEnd__48);
    }
    static {
        daysInMonth__33 = List.of(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);
        dayOfWeekLookupTableLeapy__34 = List.of(0, 0, 3, 4, 0, 2, 5, 0, 3, 6, 1, 4, 6);
        dayOfWeekLookupTableNotLeapy__35 = List.of(0, 0, 3, 3, 6, 1, 4, 6, 2, 5, 0, 3, 5);
    }
}
