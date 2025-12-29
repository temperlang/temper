package temper.core;

import java.util.function.Supplier;
import java.util.Objects;

/**
 * A supplier of {@link Generator.Result results}.
 *
 * Generator is <i>monotonic</i>; after the first time {@link Generator#getNext} returns a
 * {@link Generator.DoneResult} it will do every time *next* is called for that generator.
 *
 * Closing releases resources associated with computing subsequent results.
 */
public abstract class Generator<T> implements AutoCloseable, Supplier<Generator.Result<T>> {
    private boolean done;

    protected abstract Result<T> getNext();

    /**
     * Computes the next result.
     *
     * @return {@link DoneResult} if {@link #getDone}() is true.
     */
    @Override
    public final Result<T> get() {
        if (done) {
            return DoneResult.get();
        } else {
            boolean gotValueResult = false;
            try {
                Result<T> result = getNext();
                gotValueResult = result instanceof ValueResult;
                return result;
            } finally {
                if (!gotValueResult) {
                    done = true;
                }
            }
        }
    }

    /**
     * May be overridden to release resources needed by {@link #getNext()}
     * but should call `super.close()` to mark done so that {@link #getNext()}
     * will not be called again.
     */
    @Override
    public void close() {
        done = true;
    }

    /**
     * True if there are no more results, either because {@link AutoCloseable#close}
     * was called or because {@link #getNext} returned a {@link DoneResult} some time in
     * the past.
     */
    public final boolean getDone() {
        return done;
    }

    public static abstract class Result<T> {
        Result() {}
    }

    /**
     * A result from {@link Generator#getNext} that holds a value and indicates
     * that getting another result might yield more.
     */
    public static final class ValueResult<T> extends Result<T> {
        public final T value;
        public ValueResult(T value) { this.value = value; }
        @Override public String toString() { return "ValueResult(" + value + ")"; }
        /** ValueResults have structural equality. */
        @Override public boolean equals(Object other) {
            return other instanceof ValueResult &&
                Objects.equals(this.value, ((ValueResult) other).value);
        }
        @Override public int hashCode() {
            return 0x682a14f3 ^ Objects.hashCode(value);
        }
    }

    /** A singleton that indicates a {@link Generator} will produce no more results. */
    public static final class DoneResult extends Result<@Nullable Object> {
        private DoneResult() {}
        private static final DoneResult singleton = new DoneResult();
        public static <T> Result<T> get() {
            // DoneResult does not depend on its super types's type parameter,
            // and there can exist no subclasses of it which could.
            @SuppressWarnings("unchecked")
            Result<T> doneResult = (Result<T>) singleton;
            return doneResult;
        }
    }
}
