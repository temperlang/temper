package temper.core;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class Util {
    private Util() {}

    private enum State {
        Clear, Stop, Okay
    }

    /**
     * <p>A class to write next-only iterators. The advance method returns `stop()` to
     * indicate the iterator is exhausted. Note: nulls will <i>not</i> stop iteration.
     * </p>
     * @param <E> the element type
     * @see #stop() the stop method will halt iteration.
     */
    public abstract static class NextIterator<E> implements Iterator<E> {

        private E elem = null;
        private State state = State.Clear;

        /**
         * Sets a stop flag.
         * @return returns a null that's ignored
         */
        protected final E stop() {
            state = State.Stop;
            return null;
        }

        /**
         * @return the next element, or {@link #stop} when done.
         */
        protected abstract E advance();

        private State checkAndAdvance() {
            State state = this.state;
            if (state != State.Clear) {
                return state;
            }
            boolean okay = false;
            try {
                elem = advance();
                okay = true;
            } finally {
                if (!okay) {
                    this.state = State.Stop;
                }
            }
            if (this.state == State.Clear) {
                this.state = state = State.Okay;
            }
            return state;
        }

        @Override
        public final boolean hasNext() {
            return checkAndAdvance() == State.Okay;
        }

        @Override
        public final E next() {
            if (checkAndAdvance() == State.Stop) {
                throw new NoSuchElementException();
            }
            E elem = this.elem;
            this.elem = null;
            state = State.Clear;
            return elem;
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            while (checkAndAdvance() == State.Okay) {
                E elem = this.elem;
                this.elem = null;
                state = State.Clear;
                action.accept(elem);
            }
        }
    }

    /**
     * <p>A class to write next-only iterators. The advance method returns `stop()` to
     * indicate the iterator is exhausted. Note: nulls will <i>not</i> stop iteration.
     * </p>
     * <p>This class is specialized for int iterators.
     * </p>
     * @see #stop() the stop method will halt iteration.
     */
    public abstract static class NextIntIterator implements PrimitiveIterator.OfInt {

        private int elem = 0;
        private State state = State.Clear;

        /**
         * @return returns 0, but sets the state to Stop.
         */
        protected final int stop() {
            state = State.Stop;
            return 0;
        }

        /**
         * @return the next element, or {@link #stop} when done.
         */
        protected abstract int advance();

        private State checkAndAdvance() {
            State state = this.state;
            if (state != State.Clear) {
                return state;
            }
            boolean okay = false;
            try {
                elem = advance();
                okay = true;
            } finally {
                if (!okay) {
                    this.state = State.Stop;
                }
            }
            if (this.state == State.Clear) {
                this.state = state = State.Okay;
            }
            return state;
        }

        @Override
        public final boolean hasNext() {
            return checkAndAdvance() != State.Stop;
        }

        @Override
        public final int nextInt() {
            if (checkAndAdvance() == State.Stop) {
                throw new NoSuchElementException();
            }
            state = State.Clear;
            return elem;
        }

        @Override
        public void forEachRemaining(IntConsumer action) {
            while (checkAndAdvance() == State.Okay) {
                action.accept(elem);
                state = State.Clear;
            }
        }
    }
}
