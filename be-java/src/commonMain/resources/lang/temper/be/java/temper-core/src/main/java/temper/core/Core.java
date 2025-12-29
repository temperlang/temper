package temper.core;

import java.io.UnsupportedEncodingException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.copySign;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

/**
 * A support class for the temper core libraries.
 */
public final class Core {
    private Core() {
    }

    /** Do nothing with a value. */
    public static void doNothing(Object val) {}

    /**
     * @param klass the class literal to cast to
     * @param value the value to cast
     * @return the value cast to the new type
     * @param <T> the governing type
     */
    public static <T> T cast(Class<? extends T> klass, Object value) {
        // TODO Inline?
        return klass.cast(value);
    }

    /**
     * Casts but throws [{@link NullPointerException}] if value is null.
     * @param klass the class literal to cast to
     * @param value the value to cast
     * @return the value cast to the new type
     * @param <T> the governing type
     */
    public static <T> T castToNonNull(Class<? extends T> klass, Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return klass.cast(value);
    }

    /**
     * Checked cast from Temper *StringIndexOption* to Temper *NoStringIndex*
     * @return -1, the representation of Temper *StringIndex.none*.
     */
    public static int requireNoStringIndex(int i) {
        if (i < 0) { return -1; }
        throw new IllegalArgumentException("Required NoStringIndex but got " + i);
    }

    /**
     * Checked cast from Temper *StringIndexOption* to Temper *StringIndex*
     * @return A valid string index, &gt;= 0
     */
    public static int requireStringIndex(int i) {
        if (i >= 0) { return i; }
        throw new IllegalArgumentException("Required StringIndex but got " + i);
    }

    /**
     * A simple print command.
     * @param text the text to print.
     */
    public static void print(String text) {
        System.out.println(text);
    }

    /** A visible interface for the console. */
    public interface Console {
        /**
         * Log a message to the console.
         * @param message Has to be a String for now, but typing info sometimes requires Object, but we likely want to
         *                support general Object values in the future at some point, anyway, so eh?
         */
        void log(String message);
    }

    /** A pseudo-global console associated with a logger. */
    public static final class GlobalConsole implements Console {
        private final Logger logger;

        public GlobalConsole(Logger logger) {
            this.logger = logger;
        }

        /** 0 is getStackTrace, 1 is this log helper, and 2 is our caller, which we want. */
        private static final int CALLER_FRAME_INDEX = 2;

        @Override
        public void log(String message) {
            if (logger.isLoggable(Level.INFO)) {
                // If left null, these aren't included by Java-builtin formatters.
                String sourceClass = null;
                String sourceMethod = null;
                try {
                    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
                    if (trace.length > CALLER_FRAME_INDEX) {
                        // Alternatively, pre-configure property jdk.logger.packages,
                        // but that's officially nonstandard and subject to change.
                        StackTraceElement frame = trace[CALLER_FRAME_INDEX];
                        sourceClass = frame.getClassName();
                        sourceMethod = frame.getMethodName();
                    }
                } catch (SecurityException ignore) {
                    // Ignore.
                }
                logger.logp(Level.INFO, sourceClass, sourceMethod, message);
            }
        }
    }

    /**
     * Configure java.util.logging to log simple messages directly to System.out.
     * Designed for simple test situations, not robust usage.
     */
    public static void initSimpleLogging() {
        Logger root = Logger.getLogger("");
        // We get an array from getHandlers, so the loop should be fine.
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        };
        StreamHandler handler = new StreamHandler(System.out, formatter) {
            @Override
            public void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        try {
            handler.setEncoding(System.getProperty("file.encoding"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        root.addHandler(handler);
    }

    /**
     * Create a pseudo-global console for a given logger.
     * @param logger the Logger instance to use
     */
    public static GlobalConsole getConsole(Logger logger) {
        return new GlobalConsole(logger);
    }

    /**
     * Integer division, rethrowing.
     * @param left the numerator
     * @param right the denominator
     * @return the quotient
     */
    public static int divIntInt(int left, int right) {
        if (right == 0) {
            throw bubble();
        }
        return left / right;
    }

    /**
     * 64-bit integer division, rethrowing.
     * @param left the numerator
     * @param right the denominator
     * @return the quotient
     */
    public static long divIntInt(long left, long right) {
        if (right == 0) {
            throw bubble();
        }
        return left / right;
    }

    /**
     * Integer modulo, rethrowing.
     * @param left the numerator
     * @param right the denominator
     * @return the quotient
     */
    public static int modIntInt(int left, int right) {
        if (right == 0) {
            throw bubble();
        }
        return left % right;
    }

    /**
     * 64-bit integer modulo, rethrowing.
     * @param left the numerator
     * @param right the denominator
     * @return the quotient
     */
    public static long modIntInt(long left, long right) {
        if (right == 0) {
            throw bubble();
        }
        return left % right;
    }

    /**
     * <p>Generic comparison; assumes elements implements Comparable.
     * </p>
     * @param left an instance of Comparable
     * @param right an instance of Comparable
     * @return the result of comparing per the standard Comparator contract.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int genericCmp(Object left, Object right) {
        if (left == null) {
            return -1;
        } else if (right == null) {
            return 1;
        } else {
            return ((Comparable) left).compareTo(right);
        }
    }

    /**
     * <p>Obtain or create an exception that can be thrown for Temper bubbling.
     * </p>
     * <p>This must be `throw`n by the caller, to help the compiler with flow analysis.
     * </p>
     */
    public static RuntimeException bubble() {
        return new RuntimeException();
    }

    /**
     * <p>Throws an exception for Temper bubbling.
     * </p>
     * @param <T> an arbitrary type; allows this expression to effectively return bottom
     */
    public static <T> T throwBubble() {
        throw bubble();
    }

    /** Convenience for throwing assertion errors as an expression.  */
    public static <T> T throwAssertionError(String message) { throw new AssertionError(message); }

    private static final Pattern floatExponent = Pattern.compile("E([+-]?)");

    /**
     * @param x the value to compare
     * @param y the value to compare with
     * @return whether the values are within tolerances
     */
    public static boolean float64Near(double x, double y) {
        return float64Near(x, y, null, null);
    }

    /**
     * @param x the value to compare
     * @param y the value to compare with
     * @param relTol the tolerance relative to the max of x and y
     * @return whether the values are within tolerances
     */
    public static boolean float64Near(double x, double y, Double relTol) {
        return float64Near(x, y, relTol, null);
    }

    /**
     * @param x the value to compare
     * @param y the value to compare with
     * @param relTol the tolerance relative to the max of x and y
     * @param absTol the absolute tolerance allowed if outside relTol
     * @return whether the values are within tolerances
     */
    public static boolean float64Near(double x, double y, Double relTol, Double absTol) {
        double rel = relTol == null ? 1e-9 : relTol;
        double abs = absTol == null ? 0.0 : absTol;
        double margin = Math.max(Math.max(Math.abs(x), Math.abs(y)) * rel, abs);
        return Math.abs(x - y) < margin;
    }

    /**
     * @param n the value to convert to an int
     * @throws RuntimeException if the result can't be expressed with less than an error of 1
     * @return the value as an int
     */
    public static int float64ToInt(double n) {
        // Use double 1.0 here for immediate promotion.
        if (n > Integer.MIN_VALUE - 1.0 && n < Integer.MAX_VALUE + 1.0) {
            return (int) n;
        } else {
            // NaN should also end up here due to false comparison above.
            throw bubble();
        }
    }

    /**
     * @param n the value to convert to a long
     * @throws RuntimeException if the result can't be expressed with less than an error of 1
     * @return the value as a long
     */
    public static long float64ToInt64(double n) {
        if (n >= -MANTISSA_LIMIT && n <= MANTISSA_LIMIT) {
            return (long) n;
        } else {
            // NaN should also end up here due to false comparison above.
            throw bubble();
        }
    }

    /**
     * Implements connected method {@code Float64::toString}.
     * TODO It might be possible to do this more succinctly with DecimalFormat.
     */
    public static String float64ToString(double n) {
        if (n == 0.0) {
            if (copySign(1.0d, n) > 0.0) {
                return "0.0";
            } else {
                return "-0.0";
            }
        } else {
            String d = Double.toString(n);
            Matcher m = floatExponent.matcher(d);
            // Matcher.replaceFirst is 9+
            if (m.find()) {
                String sign = m.group(1);
                if (sign.isEmpty()) {
                    sign = "+";
                }
                return d.substring(0, m.start()) + "e" + sign + d.substring(m.end());
            } else {
                return d;
            }
        }
    }

    /**
     * @param n the value to convert to a double
     * @throws RuntimeException if the result can't be expressed
     * @return the value as a double
     */
    public static double int64ToFloat64(long n) {
        if (n >= -MANTISSA_LIMIT && n <= MANTISSA_LIMIT) {
            return (double) n;
        } else {
            throw bubble();
        }
    }

    private static final long MANTISSA_LIMIT = (1L << 53) - 1;

    /**
     * @param n the value to convert to an int
     * @throws RuntimeException if the result can't be expressed
     * @return the value as an int
     */
    public static int int64ToInt(long n) {
        if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
            return (int) n;
        } else {
            throw bubble();
        }
    }

    private static void denySurrogate(int codePoint) {
        // Use custom logic because Character.isSurrogate works on char, not int.
        if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
            // Use error message similar to code point bounds message, which is checked elsewhere.
            // Focus on Unicode scalar value wording, because it might explain motive better than avoiding surrogates.
            throw new IllegalArgumentException(String.format("Not a valid Unicode scalar value: 0x%X", codePoint));
        }
    }

    public static String stringFromCodePoint(int codePoint) {
        denySurrogate(codePoint);
        return new String(Character.toChars(codePoint));
    }

    public static String stringFromCodePoints(List<Integer> codePoints) {
        StringBuilder builder = new StringBuilder();
        for (int codePoint : codePoints) {
            denySurrogate(codePoint);
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }

    public static int stringCountBetween(String s, int begin, int end) {
        int length = s.length();
        begin = Math.min(begin, length);
        end = Math.max(begin, Math.min(end, length));
        int count = 0;
        for (int i = begin; i < end;) {
            int cp = s.codePointAt(i);
            count += 1;
            i += Character.charCount(cp);
        }
        return count;
    }

    public static boolean stringHasAtLeast(String s, int begin, int end, int minCount) {
        int length = s.length();
        begin = Math.min(begin, length);
        end = Math.max(begin, Math.min(end, length));
        int nUtf16 = end - begin;
        if (nUtf16 < minCount) { return false; }
        if (nUtf16 >= minCount * 2) { return true; }
        // Fall back to an early-outing version of countBetween.
        int count = 0;
        for (int i = begin; i < end;) {
            int cp = s.codePointAt(i);
            count += 1;
            if (count >= minCount) { return true; }
            i += Character.charCount(cp);
        }
        return count >= minCount;
    }

    public static boolean stringHasIndex(String s, int i) {
        return i < s.length();
    }

    public static int stringNext(String s, int i) {
        int length = s.length();
        if (i >= length) { return length; }
        return i + Character.charCount(s.codePointAt(i));
    }

    public static int stringPrev(String s, int i) {
        int length = s.length();
        if (i <= 1) { return 0; } // Can't be a surrogate pair before.
        if (i > length) { return length; }
        int cp = s.codePointAt(i - 2);
        return (cp >= 0x1_0000) ? i - 2 : i - 1;
    }

    public static int stringStep(String s, int i, int by) {
        int newI = i;
        if (by >= 0) {
            steps: for (int j = 0; j < by; j += 1) {
                int oldI = newI;
                newI = stringNext(s, newI);
                if (newI == oldI) {
                    break steps;
                }
            }
        } else {
            steps: for (int j = 0; j > by; j -= 1) {
                int oldI = newI;
                newI = stringPrev(s, newI);
                if (newI == oldI) {
                    break steps;
                }
            }
        }
        return newI;
    }

    public static void stringForEach(String s, IntConsumer f) {
        for (int i = 0, length = s.length(); i < length;) {
            int cp = s.codePointAt(i);
            f.accept(cp);
            i += Character.charCount(cp);
        }
    }

    public static void stringBuilderAppendBetween(StringBuilder sb, String s, int begin, int end) {
        int length = s.length();
        begin = Math.min(begin, length);
        end = Math.max(begin, Math.min(end, length));
        sb.append(s, begin, end);
    }

    public static void stringBuilderAppendCodePoint(StringBuilder sb, int codePoint) {
        denySurrogate(codePoint);
        sb.appendCodePoint(codePoint);
    }

    public static String stringSlice(String s, int begin, int end) {
        int length = s.length();
        begin = Math.min(begin, length);
        end = Math.max(begin, Math.min(end, length));
        return s.substring(begin, end);
    }

    /**
     * Split a string over a delimiter.
     * @param source a well-formed string
     * @param delimiter a well-formed delimiter
     * @return an immutable list of strings
     */
    public static List<String> stringSplit(String source, String delimiter) {

        int sourceLen = source.length();
        int delimLen = delimiter.length();
        if (delimLen == 0) {
            ArrayList<String> result = new ArrayList<>(source.length());
            int i = 0;
            while (i < sourceLen) {
                int j = i + Character.charCount(source.codePointAt(i));
                result.add(source.substring(i, j));
                i = j;
            }
            result.trimToSize();
            return listCopyOfTrusted(result);
        }

        int count = 1; // Include the substring trailing the last delimiter.
        int index = 0;
        int nextDelim;

        while((nextDelim = source.indexOf(delimiter, index)) >= 0) {
            count ++;
            index = nextDelim + delimLen;
        }
        ArrayList<String> result = new ArrayList<>(count);
        index = 0;
        while((nextDelim = source.indexOf(delimiter, index)) >= 0) {
            result.add(source.substring(index, nextDelim));
            index = nextDelim + delimLen;
        }
        result.add(source.substring(index));
        result.trimToSize();
        return listCopyOfTrusted(result);
    }

    public static double stringToFloat64(String s) {
        String trimmed = s.trim();
        if (trimmed.startsWith(".") || trimmed.endsWith(".")) {
            throw bubble();
        }
        return Double.parseDouble(s);
    }

    public static int stringToInt(String s) {
        return stringToInt(s, 10);
    }

    public static int stringToInt(String s, int radix) {
        return Integer.parseInt(s.trim(), radix);
    }

    public static long stringToInt64(String s) {
        return stringToInt64(s, 10);
    }

    public static long stringToInt64(String s, int radix) {
        return Long.parseLong(s.trim(), radix);
    }

    /**
     * Emulates List.of() in Java 9+.
     * @return an empty immutable list.
     * @param <E> the element type
     */
    public static <E> List<E> listOf() {
        return emptyList();
    }

    /**
     * Emulates List.of() in Java 9+.
     * @return a singleton immutable list
     * @param <E> the element type
     */
    public static <E> List<E> listOf(E elem) {
        return singletonList(elem);
    }

    /**
     * Emulates List.of() in Java 9+.
     * @return an immutable list
     * @param <E> the element type
     */
    @SafeVarargs
    public static <E> List<E> listOf(E ... elem) {
        return listCopyOfTrusted(Arrays.asList(elem));
    }

    /**
     * Emulates List.copyOf() in Java 9+.
     * @return an immutable list
     * @param <E> the element type
     */
    public static <E> List<E> listCopyOf(Collection<E> elems) {
        return listCopyOfTrusted(new ArrayList<>(elems));
    }

    /**
     * @param elems a trusted list that won't be modified
     * @return an immutable list
     * @param <E> the element type
     */
    private static <E> List<E> listCopyOfTrusted(List<E> elems) {
        switch(elems.size()) {
            case 0:
                return emptyList();
            case 1:
                return singletonList(elems.iterator().next());
            default:
                return unmodifiableList(elems);
        }
    }

    /**
     * @param target a ListBuilder instance
     * @param elem the element to add
     * @param <E> the element type
     */
    public static <E> void listAdd(List<E> target, E elem) {
        target.add(elem);
    }

    /**
     * @param target a ListBuilder instance
     * @param elem the element to add
     * @param at where to insert the element
     * @param <E> the element type
     */
    public static <E> void listAdd(List<E> target, E elem, int at) {
        // TODO Inline?
        target.add(at, elem);
    }

    /**
     * @param target a ListBuilder instance
     * @param source the source of the elements to add
     * @param <E> the element type
     */
    public static <E> void listAddAll(List<E> target, Collection<? extends E> source) {
        target.addAll(source);
    }

    /**
     * @param target a ListBuilder instance
     * @param source the source of the elements to add
     * @param at where to insert the elements
     * @param <E> the element type
     */
    public static <E> void listAddAll(List<E> target, Collection<? extends E> source, int at) {
        // TODO Inline?
        target.addAll(at, source);
    }

    /**
     * @param target a ListBuilder instance
     * @return the removed element
     * @param <E> the element type
     */
    public static <E> E listRemoveLast(List<E> target) {
        // TODO Inline?
        return target.remove(target.size() - 1);
    }

    /**
     * @param target a ListBuilder instance
     * @param <E> the element type
     */
    public static <E> void listSort(List<E> target, ToIntBiFunction<E, E> compare) {
        target.sort((a, b) -> compare.applyAsInt(a, b));
    }

    /**
     * @param target a ListBuilder instance
     */
    public static void listSortInt(List<Integer> target, IntBinaryOperator compare) {
        target.sort((a, b) -> compare.applyAsInt(a, b));
    }

    /**
     * @param target a Listed instance
     * @return the sorted list
     * @param <E> the element type
     */
    public static <E> List<E> listSorted(List<E> target, ToIntBiFunction<E, E> compare) {
        List<E> result = new ArrayList<>(target);
        listSort(result, compare);
        return listCopyOfTrusted(result);
    }

    /**
     * @param target a Listed instance
     * @return the sorted list
     */
    public static List<Integer> listSortedInt(List<Integer> target, IntBinaryOperator compare) {
        List<Integer> result = new ArrayList<>(target);
        listSortInt(result, compare);
        return listCopyOfTrusted(result);
    }

    /**
     * @param target a ListBuilder instance
     * @return the removed list
     * @param <E> the element type
     */
    public static <E> List<E> listSplice(List<E> target) {
        return listSplice(target, null, null, null);
    }

    /**
     * @param target a ListBuilder instance
     * @param index where to start removing from, defaulting to 0
     * @return the removed list
     * @param <E> the element type
     */
    public static <E> List<E> listSplice(List<E> target, Integer index) {
        return listSplice(target, index, null, null);
    }

    /**
     * @param target a ListBuilder instance
     * @param index where to start removing from, defaulting to 0
     * @param removeCount how many to remove from, defaulting to all after index
     * @return the removed list
     * @param <E> the element type
     */
    public static <E> List<E> listSplice(List<E> target, Integer index, Integer removeCount) {
        return listSplice(target, index, removeCount, null);
    }

    /**
     * @param target a ListBuilder instance
     * @param index where to start removing from, defaulting to 0
     * @param removeCount how many to remove from, defaulting to all after index
     * @param newElems the elements to put in place of removed elements, defaulting to none
     * @return the removed elements as an immutable list
     * @param <E> the element type
     */
    public static <E> List<E> listSplice(List<E> target, Integer index, Integer removeCount, List<E> newElems) {
        int size = target.size();
        int sliceStart = index != null ? clamp(index, 0, size) : 0;
        int removeClamped = removeCount != null ? clamp(removeCount, 0, size) : size;
        int sliceEnd = Math.min(sliceStart + removeClamped, size);
        List<E> slice = target.subList(sliceStart, sliceEnd);
        List<E> result = listCopyOf(slice);
        slice.clear();
        if (newElems != null) {
            slice.addAll(newElems);
        }
        return result;
    }

    /**
     * <p>Implements {@code List::filter}.
     * </p>
     * @param source read once for its contents
     * @param predicate result has values for which this returns true
     * @return the filtered list
     * @param <E> the element type
     */
    public static <E> List<E> listFilterObj(List<E> source, Predicate<E> predicate) {
        ArrayList<E> result = new ArrayList<>(source.size());
        for (E elem : source) {
            if (predicate.test(elem)) {
                result.add(elem);
            }
        }
        result.trimToSize();
        return listCopyOfTrusted(result);
    }

    /**
     * <p>Implements {@code List::filter}.
     * </p>
     * @param source read once for its contents
     * @param predicate result has values for which this returns true
     * @return the filtered list
     */
    public static List<Boolean> listFilterBool(List<Boolean> source, Predicate<Boolean> predicate) {
        ArrayList<Boolean> result = new ArrayList<>(source.size());
        for (Boolean elem : source) {
            if (predicate.test(elem)) {
                result.add(elem);
            }
        }
        result.trimToSize();
        return listCopyOfTrusted(result);
    }


    /**
     * <p>Implements {@code List::filter}.
     * </p>
     * @param source read once for its contents
     * @param predicate result has values for which this returns true
     * @return the filtered list
     */
    public static List<Integer> listFilterInt(List<Integer> source, IntPredicate predicate) {
        ArrayList<Integer> result = new ArrayList<>(source.size());
        for (int elem : source) {
            if (predicate.test(elem)) {
                result.add(elem);
            }
        }
        result.trimToSize();
        return listCopyOfTrusted(result);
    }

    /**
     * <p>Implements {@code List::filter}.
     * </p>
     * @param source read once for its contents
     * @param predicate result has values for which this returns true
     * @return the filtered list
     */
    public static List<Double> listFilterDouble(List<Double> source, DoublePredicate predicate) {
        ArrayList<Double> result = new ArrayList<>(source.size());
        for (double elem : source) {
            if (predicate.test(elem)) {
                result.add(elem);
            }
        }
        result.trimToSize();
        return listCopyOfTrusted(result);
    }

    /**
     * @param source accessed randomly for the element
     * @param index the index to the element
     * @return the element, possibly throwing a RuntimeException if OOB
     * @param <E> the element type
     */
    public static <E> E listGet(List<E> source, int index) {
        // TODO Inline?
        return source.get(index);
    }

    /**
     * @param source accessed randomly for the element
     * @param index the index to the element
     * @param defaultValue a fallback value if the index is out of bounds
     * @return the element or defaultValue
     * @param <E> the element type
     */
    public static <E> E listGetOr(List<E> source, int index, E defaultValue) {
        if (index < 0 || index >= source.size()) {
            return defaultValue;
        }
        return source.get(index);
    }

    /**
     * <p>Implements {@code List::join}.
     * </p>
     * @param source read once for its contents
     * @param delimiter the delimiter inserted between values
     * @param function a function to convert source values to strings
     * @return the combined string
     * @param <E> the source element type
     */
    public static <E> String listJoinObj(List<E> source, String delimiter, Function<E, String> function) {
        StringBuilder sb = new StringBuilder();
        String before = "";
        for (E elem : source) {
            sb.append(before);
            sb.append(function.apply(elem));
            before = delimiter;
        }
        return sb.toString();
    }

    /**
     * <p>Implements {@code List::join}.
     * </p>
     * @param source read once for its contents
     * @param delimiter the delimiter inserted between values
     * @param function a function to convert source values to strings
     * @return the combined string
     */
    public static String listJoinBool(List<Boolean> source, String delimiter, Function<Boolean, String> function) {
        StringBuilder sb = new StringBuilder();
        String before = "";
        for (Boolean elem : source) {
            sb.append(before);
            sb.append(function.apply(elem));
            before = delimiter;
        }
        return sb.toString();
    }

    /**
     * <p>Implements {@code List::join}.
     * </p>
     * @param source read once for its contents
     * @param delimiter the delimiter inserted between values
     * @param function a function to convert source values to strings
     * @return the combined string
     */
    public static String listJoinInt(List<Integer> source, String delimiter, IntFunction<String> function) {
        StringBuilder sb = new StringBuilder();
        String before = "";
        for (int elem : source) {
            sb.append(before);
            sb.append(function.apply(elem));
            before = delimiter;
        }
        return sb.toString();
    }

    /**
     * <p>Implements {@code List::join}.
     * </p>
     * @param source read once for its contents
     * @param delimiter the delimiter inserted between values
     * @param function a function to convert source values to strings
     * @return the combined string
     */
    public static String listJoinDouble(List<Double> source, String delimiter, DoubleFunction<String> function) {
        StringBuilder sb = new StringBuilder();
        String before = "";
        for (double elem : source) {
            sb.append(before);
            sb.append(function.apply(elem));
            before = delimiter;
        }
        return sb.toString();
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     * @param <F> the result element type
     */
    public static <E, F> List<F> listMapObjToObj(List<E> source, Function<E, F> function) {
        List<F> result = new ArrayList<>(source.size());
        for (E elem : source) {
            result.add(function.apply(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <F> the result element type
     */
    public static <F> List<F> listMapIntToObj(List<Integer> source, IntFunction<F> function) {
        List<F> result = new ArrayList<>(source.size());
        for (int elem : source) {
            result.add(function.apply(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Boolean> listMapDoubleToObj(List<Double> source, DoubleFunction<Boolean> function) {
        List<Boolean> result = new ArrayList<>(source.size());
        for (double elem : source) {
            result.add(function.apply(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     */
    public static <E> List<Boolean> listMapObjToBool(List<E> source, Predicate<E> function) {
        List<Boolean> result = new ArrayList<>(source.size());
        for (E elem : source) {
            result.add(function.test(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Boolean> listMapIntToBool(List<Integer> source, IntPredicate function) {
        List<Boolean> result = new ArrayList<>(source.size());
        for (int elem : source) {
            result.add(function.test(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Boolean> listMapDoubleToBool(List<Double> source, DoublePredicate function) {
        List<Boolean> result = new ArrayList<>(source.size());
        for (double elem : source) {
            result.add(function.test(elem));
        }
        return unmodifiableList(result);
    }
    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     */

    public static <E> List<Integer> listMapObjToInt(List<E> source, ToIntFunction<E> function) {
        List<Integer> result = new ArrayList<>(source.size());
        for (E elem : source) {
            result.add(function.applyAsInt(elem));
        }
        return unmodifiableList(result);
    }
    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Integer> listMapIntToInt(List<Integer> source, IntUnaryOperator function) {
        List<Integer> result = new ArrayList<>(source.size());
        for (int elem : source) {
            result.add(function.applyAsInt(elem));
        }
        return unmodifiableList(result);
    }
    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Integer> listMapDoubleToInt(List<Double> source, DoubleToIntFunction function) {
        List<Integer> result = new ArrayList<>(source.size());
        for (double elem : source) {
            result.add(function.applyAsInt(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     */
    public static <E> List<Double> listMapObjToDouble(List<E> source, ToDoubleFunction<E> function) {
        List<Double> result = new ArrayList<>(source.size());
        for (E elem : source) {
            result.add(function.applyAsDouble(elem));
        }
        return unmodifiableList(result);
    }
    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Double> listMapIntToDouble(List<Integer> source, IntToDoubleFunction function) {
        List<Double> result = new ArrayList<>(source.size());
        for (int elem : source) {
            result.add(function.applyAsDouble(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::map}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Double> listMapDoubleToDouble(List<Double> source, DoubleUnaryOperator function) {
        List<Double> result = new ArrayList<>(source.size());
        for (double elem : source) {
            result.add(function.applyAsDouble(elem));
        }
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     * @param <F> the result element type
     */
    public static <E, F> List<F> listMapDroppingObjToObj(List<E> source, Function<E, F> function) {
        ArrayList<F> result = new ArrayList<>(source.size());
        for (E elem : source) {
            F mapped;
            try {
                mapped = function.apply(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <F> the result element type
     */
    public static <F> List<F> listMapDroppingIntToObj(List<Integer> source, IntFunction<F> function) {
        ArrayList<F> result = new ArrayList<>(source.size());
        for (int elem : source) {
            F mapped;
            try {
                mapped = function.apply(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <F> the result element type
     */
    public static <F> List<F> listMapDroppingDoubleToObj(List<Double> source, DoubleFunction<F> function) {
        ArrayList<F> result = new ArrayList<>(source.size());
        for (double elem : source) {
            F mapped;
            try {
                mapped = function.apply(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }
    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     */
    public static <E> List<Boolean> listMapDroppingObjToBool(List<E> source, Predicate<E> function) {
        ArrayList<Boolean> result = new ArrayList<>(source.size());
        for (E elem : source) {
            Boolean mapped;
            try {
                mapped = function.test(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Boolean> listMapDroppingIntToBool(List<Integer> source, IntPredicate function) {
        ArrayList<Boolean> result = new ArrayList<>(source.size());
        for (int elem : source) {
            Boolean mapped;
            try {
                mapped = function.test(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Boolean> listMapDroppingDoubleToBool(List<Double> source, DoublePredicate function) {
        ArrayList<Boolean> result = new ArrayList<>(source.size());
        for (double elem : source) {
            Boolean mapped;
            try {
                mapped = function.test(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     */
    public static <E> List<Integer> listMapDroppingObjToInt(List<E> source, ToIntFunction<E> function) {
        ArrayList<Integer> result = new ArrayList<>(source.size());
        for (E elem : source) {
            int mapped;
            try {
                mapped = function.applyAsInt(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Integer> listMapDroppingIntToInt(List<Integer> source, IntUnaryOperator function) {
        ArrayList<Integer> result = new ArrayList<>(source.size());
        for (int elem : source) {
            int mapped;
            try {
                mapped = function.applyAsInt(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Integer> listMapDroppingDoubleToInt(List<Double> source, DoubleToIntFunction function) {
        ArrayList<Integer> result = new ArrayList<>(source.size());
        for (double elem : source) {
            int mapped;
            try {
                mapped = function.applyAsInt(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     * @param <E> the source element type
     */
    public static <E> List<Double> listMapDroppingObjToDouble(List<E> source, ToDoubleFunction<E> function) {
        ArrayList<Double> result = new ArrayList<>(source.size());
        for (E elem : source) {
            double mapped;
            try {
                mapped = function.applyAsDouble(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Double> listMapDroppingIntToDouble(List<Integer> source, IntToDoubleFunction function) {
        ArrayList<Double> result = new ArrayList<>(source.size());
        for (int elem : source) {
            double mapped;
            try {
                mapped = function.applyAsDouble(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    /**
     * <p>Implements {@code Listed::mapDropping}.
     * </p>
     * @param source read once for its contents
     * @param function converts source values to result values
     * @return the remapped list, with the same length
     */
    public static List<Double> listMapDroppingDoubleToDouble(List<Double> source, DoubleUnaryOperator function) {
        ArrayList<Double> result = new ArrayList<>(source.size());
        for (double elem : source) {
            double mapped;
            try {
                mapped = function.applyAsDouble(elem);
            } catch(RuntimeException ignored) {
                continue;
            }
            result.add(mapped);
        }
        result.trimToSize();
        return unmodifiableList(result);
    }

    public interface BooleanBiPredicate {
        boolean test(boolean left, boolean right);
    }

    /** <p>Implements {@code Listed::reduce}.</p> */
    public static boolean listedReduceBool(List<Boolean> source, BooleanBiPredicate reducer) {
        return listedReduceBoolToBool(source, source.get(0), 1, (reduction, item) -> reducer.test(reduction, item));
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static boolean listedReduceBoolToBool(List<Boolean> source, boolean initial, BooleanBiPredicate reducer) {
        return listedReduceBoolToBool(source, initial, 0, reducer);
    }

    static boolean listedReduceBoolToBool(List<Boolean> source, boolean initial, int index, BooleanBiPredicate reducer) {
        boolean result = initial;
        for (int i = index; i < source.size(); i += 1) {
            result = reducer.test(result, source.get(i));
        }
        return result;
    }

    /** <p>Implements {@code Listed::reduce}.</p> */
    public static double listedReduceDouble(List<Double> source, DoubleBinaryOperator reducer) {
        return listedReduceDoubleToDouble(source, source.get(0), 1, (reduction, item) -> reducer.applyAsDouble(reduction, item));
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static double listedReduceDoubleToDouble(List<Double> source, double initial, DoubleBinaryOperator reducer) {
        return listedReduceDoubleToDouble(source, initial, 0, reducer);
    }

    static double listedReduceDoubleToDouble(List<Double> source, double initial, int index, DoubleBinaryOperator reducer) {
        double result = initial;
        for (int i = index; i < source.size(); i += 1) {
            result = reducer.applyAsDouble(result, source.get(i));
        }
        return result;
    }

    /** <p>Implements {@code Listed::reduce}.</p> */
    public static int listedReduceInt(List<Integer> source, IntBinaryOperator reducer) {
        return listedReduceIntToInt(source, source.get(0), 1, (reduction, item) -> reducer.applyAsInt(reduction, item));
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static int listedReduceIntToInt(List<Integer> source, int initial, IntBinaryOperator reducer) {
        return listedReduceIntToInt(source, initial, 0, reducer);
    }

    static int listedReduceIntToInt(List<Integer> source, int initial, int index, IntBinaryOperator reducer) {
        int result = initial;
        for (int i = index; i < source.size(); i += 1) {
            result = reducer.applyAsInt(result, source.get(i));
        }
        return result;
    }

    /** <p>Implements {@code Listed::reduce}.</p> */
    public static <R> R listedReduceObj(List<R> source, BinaryOperator<R> reducer) {
        return listedReduceObjToObj(source, source.get(0), 1, (reduction, item) -> reducer.apply(reduction, item));
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static <R> R listedReduceObjToObj(List<R> source, R initial, BinaryOperator<R> reducer) {
        return listedReduceObjToObj(source, initial, 0, reducer);
    }

    static <R> R listedReduceObjToObj(List<R> source, R initial, int index, BinaryOperator<R> reducer) {
        R result = initial;
        for (int i = index; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface BoolToDoubleReducer {
        double apply(double reduction, boolean element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static double listedReduceBoolToDouble(List<Boolean> source, double initial, BoolToDoubleReducer reducer) {
        double result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface BoolToIntReducer {
        int apply(int reduction, boolean element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static int listedReduceBoolToInt(List<Boolean> source, int initial, BoolToIntReducer reducer) {
        int result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface BoolToObjReducer<R> {
        R apply(R reduction, boolean element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static <R> R listedReduceBoolToObj(List<Boolean> source, R initial, BoolToObjReducer<R> reducer) {
        R result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface DoubleToBoolReducer {
        boolean apply(boolean reduction, double element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static boolean listedReduceDoubleToBool(List<Double> source, boolean initial, DoubleToBoolReducer reducer) {
        boolean result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface DoubleToIntReducer {
        int apply(int reduction, double element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static int listedReduceDoubleToInt(List<Double> source, int initial, DoubleToIntReducer reducer) {
        int result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface DoubleToObjReducer<R> {
        R apply(R reduction, double element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static <R> R listedReduceDoubleToObj(List<Double> source, R initial, DoubleToObjReducer<R> reducer) {
        R result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface IntToBoolReducer {
        boolean apply(boolean reduction, int element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static boolean listedReduceIntToBool(List<Integer> source, boolean initial, IntToBoolReducer reducer) {
        boolean result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface IntToDoubleReducer {
        double apply(double reduction, int element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static double listedReduceIntToDouble(List<Integer> source, double initial, IntToDoubleReducer reducer) {
        double result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface IntToObjReducer<R> {
        R apply(R reduction, int element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static <R> R listedReduceIntToObj(List<Integer> source, R initial, IntToObjReducer<R> reducer) {
        R result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface ObjToBoolReducer<R> {
        boolean apply(boolean reduction, R element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static <R> boolean listedReduceObjToBool(List<R> source, boolean initial, ObjToBoolReducer<R> reducer) {
        boolean result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface ObjToDoubleReducer<R> {
        double apply(double reduction, R element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static <R> double listedReduceObjToDouble(List<R> source, double initial, ObjToDoubleReducer<R> reducer) {
        double result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    public interface ObjToIntReducer<R> {
        int apply(int reduction, R element);
    }

    /** <p>Implements {@code Listed::reduceFrom}.</p> */
    public static <R> int listedReduceObjToInt(List<R> source, int initial, ObjToIntReducer<R> reducer) {
        int result = initial;
        for (int i = 0; i < source.size(); i += 1) {
            result = reducer.apply(result, source.get(i));
        }
        return result;
    }

    /**
     * <p>Extract a sublist from a source list. Per Temper semantics, this is a copy rather than a view.
     * </p>
     * @param source read once for its contents
     * @param startInclusive the starting index, clamped to valid indices
     * @param endExclusive the stopping index, clamped to valid indices
     * @return a slice of the source list
     * @param <E> the list element type
     */
    public static <E> List<E> listSlice(List<E> source, int startInclusive, int endExclusive) {
        int len = source.size();
        int start = clamp(startInclusive, 0, len);
        int end = clamp(endExclusive, start, len);
        int sliceLen = end - start;
        switch(sliceLen) {
            case 0:
                return emptyList();
            case 1:
                return singletonList(source.get(start));
            default:
                return unmodifiableList(new ArrayList<>(source.subList(start, end)));
        }
    }

    /**
     * <p>Return an immutable list for a maybe immutable one, returning just the original if known immutable.
     * </p>
     * @param source to return an immutable list for
     * @return a slice of the source list
     * @param <E> the list element type
     */
    public static <E> List<E> listedToList(List<E> source) {
        if (
            // Check in the order that seems most likely to happen.
            // Technically, an unmodifiable might wrap a modifiable, but not expected from Temper-made lists.
            // And we can only check so far.
            unmodifiableListClass.isInstance(source) ||
                emptyListClass.isInstance(source) ||
                singletonListClass.isInstance(source)
        ) {
            return source;
        } else {
            return listCopyOf(source);
        }
    }

    // For checking instance of private class types.
    @SuppressWarnings("rawtypes")
    private final static Class emptyListClass = emptyList().getClass();
    @SuppressWarnings("rawtypes")
    private final static Class singletonListClass = singletonList(null).getClass();
    @SuppressWarnings("rawtypes")
    private final static Class unmodifiableListClass = unmodifiableList(emptyList()).getClass();

    private static int clamp(int value, int low, int high) {
        if (value < low) {
            return low;
        } else if (value > high) {
            return high;
        } else {
            return value;
        }
    }

    /**
     * Wraps the removeFirst method.
     * @param deque a deque instance
     * @return the first element removed from the deque
     * @param <E> the element type
     */
    public static <E> E dequeRemoveFirst(Deque<E> deque) {
        // TODO Inline?
        return deque.removeFirst();
    }

    /**
     * @param entries a collection of key-value pairs
     * @return a read-only implementation of the Map interface containing the given entries
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> Map<K, V> mapConstructor(Collection<? extends Map.Entry<K, V>> entries) {
        switch(entries.size()) {
            case 0:
                return emptyMap();
            case 1:
                Map.Entry<K, V> first = entries.iterator().next();
                return singletonMap(first.getKey(), first.getValue());
            default:
                // Unfortunately, the entry set doesn't support addAll.
                Map<K, V> map = new LinkedHashMap<>(mapCalculateCapacity(entries.size()), MAP_LOAD_FACTOR);
                for (Map.Entry<K, V> entry : entries) {
                    map.put(entry.getKey(), entry.getValue());
                }
                return unmodifiableMap(map);
        }
    }

    /**
     * Calculate a capacity to avoid a HashMap resizing. This is similar to the calculation in `putAll` or the
     * `HashMap.newHashMap` function in JDK 19.
     *
     * @param numMappings the expected number of mappings
     * @return initial capacity for HashMap based classes.
     */
    private static int mapCalculateCapacity(int numMappings) {
        return (int) Math.ceil(numMappings / MAP_LOAD_FACTOR);
    }

    private static final float MAP_LOAD_FACTOR = 0.75f;

    /**
     * @param map a Mapped instance
     * @param key the key for the value to retrieve
     * @return the value, possibly null
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> V mappedGet(Map<K, V> map, K key) {
        V value = map.get(key);
        if (value == null && !map.containsKey(key)) {
            throw bubble();
        }
        return value;
    }

    /**
     * @param map a source map
     * @return an immutable list of results
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> List<Map.Entry<K, V>> mappedToList(Map<K, V> map) {
        ArrayList<Map.Entry<K, V>> list = new ArrayList<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            list.add(new SimpleImmutableEntry<K, V>(entry));
        }
        list.trimToSize();
        return listCopyOfTrusted(list);
    }

    /**
     * @param map a source map
     * @return a mutable list of results
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> ArrayList<Map.Entry<K, V>> mappedToListBuilder(Map<K, V> map) {
        ArrayList<Map.Entry<K, V>> list = new ArrayList<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            list.add(new SimpleImmutableEntry<K, V>(entry));
        }
        return list;
    }


    /**
     * @param map a source map
     * @param fn a function to call on each key and value for result
     * @return an immutable list of results
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V, T> List<T> mappedToListWith(Map<K, V> map, BiFunction<K, V, T> fn) {
        ArrayList<T> list = new ArrayList<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            list.add(fn.apply(entry.getKey(), entry.getValue()));
        }
        list.trimToSize();
        return listCopyOfTrusted(list);
    }

    /**
     * @param map a source map
     * @param fn a function to call on each key and value for result
     * @return a mutable list of results
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V, T> ArrayList<T> mappedToListBuilderWith(Map<K, V> map, BiFunction<K, V, T> fn) {
        ArrayList<T> list = new ArrayList<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            list.add(fn.apply(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    /**
     * @param map a source map
     * @param func a consumer to accept each key and value
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> void mappedForEach(Map<K, V> map, BiConsumer<K, V> func) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            func.accept(entry.getKey(), entry.getValue());
        }
    }

    /**
     * @param map a source map
     * @return the value that was mapped to the key
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> V mapBuilderRemove(Map<K, V> map, K key) {
        if(!map.containsKey(key)) {
            throw bubble();
        }
        return map.remove(key);
    }

    /**
     * @param map a source map
     * @return a readonly copy of the map
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> Map<K, V> mappedToMap(Map<K, V> map) {
        switch(map.size()) {
            case 0:
                return emptyMap();
            case 1:
                Map.Entry<K, V> entry = map.entrySet().iterator().next();
                return singletonMap(entry.getKey(), entry.getValue());
            default:
                return unmodifiableMap(new LinkedHashMap<>(map));
        }
    }

    /** Check equality between a boxed and primitive int with minimal unboxing. */
    public static boolean boxedEq(Integer left, int right) {
        return left != null && left == right;
    }

    /** Check equality between a boxed and primitive double with minimal unboxing. */
    public static boolean boxedEq(Double left, double right) {
        return left != null && Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }

    /** Check equality between a boxed and primitive int with minimal unboxing. */
    public static boolean boxedEqRev(int left, Integer right) {
        return right != null && left == right;
    }

    /** Check equality between a boxed and primitive double with minimal unboxing. */
    public static boolean boxedEqRev(double left, Double right) {
        return right != null && Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }

    /** Adapts a converted coroutine method reference or lambda to a Generator */
    public static <T> Generator<T> adaptGeneratorFn(Function<Generator<T>, Generator.Result<T>> resultFunction) {
        return new WrapFunctionGenerator<T>(resultFunction);
    }

    /** Adapts a converted coroutine method reference or lambda to a Generator */
    public static <T> Generator<T> safeAdaptGeneratorFn(Function<Generator<T>, Generator.Result<T>> resultFunction) {
        return new WrapFunctionGenerator<T>(resultFunction);
    }

    /**
     * Steps a generator on the shared fork/join thread pool.
     *
     * If the generator uses Temper await, then it will reschedule itself when
     * a promise it's waiting on completes to deal with the resolution.
     */
    public static void runAsync(Supplier<Generator<Optional<? super Object>>> generatorSupplier) {
        Generator<Optional<? super Object>> generator = generatorSupplier.get();
        ForkJoinPool.commonPool().execute(
            () -> {
                generator.get();
            }
        );
    }

    /**
     * Called from main method to wait for tasks,
     * like those scheduled via {@link #runAsync}, complete.
     */
    public static void waitUntilTasksComplete() {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        // This timeout is sufficient for functional tests.
        // If a long running main method needs more time, it should
        // negotiate promises for termination with the tasks it spawns.
        commonPool.awaitQuiescence(10L, TimeUnit.SECONDS);
    }
}

final class WrapFunctionGenerator<T> extends Generator<T> {
    private final Function<Generator<T>, Generator.Result<T>> resultFunction;
    WrapFunctionGenerator(Function<Generator<T>, Generator.Result<T>> resultFunction) {
        this.resultFunction = resultFunction;
    }

    @Override
    public Generator.Result<T> getNext() {
        return resultFunction.apply(this);
    }

    // TODO: reimplement close() to cause the converted coroutine to throw internally.
}
