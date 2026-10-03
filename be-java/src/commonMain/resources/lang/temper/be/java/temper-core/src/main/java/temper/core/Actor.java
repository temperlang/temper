package temper.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The turn of one instance of a Temper {@code @actor} class.
 *
 * <p>Generated code calls {@link #enter} before, and {@link #exit} in a
 * {@code finally} after, the body of every method, getter, setter and
 * constructor of the class, and of every step of an async block or closure
 * that the class creates and that uses {@code this}.  So turns on one
 * instance never overlap, whichever threads host code calls it from.
 *
 * <p>A thread already inside the actor enters again at once: a method that
 * calls another method on {@code this}, or a callback the actor invoked that
 * calls back in, runs inline.
 *
 * <p>A thread that would wait for an actor whose owner is itself waiting,
 * directly or through other actors, on that thread, throws
 * {@link CycleException} instead of deadlocking.  The wait-for graph is only
 * consulted when the actor is busy, so an uncontended call costs what an
 * uncontended {@link ReentrantLock} does.
 *
 * <p>An exception, a Temper bubble or panic, leaves the turn through the
 * {@code finally}, so the actor is released and stays usable.
 */
public final class Actor {
    private final Lock lock = new Lock();

    private static final Object GRAPH = new Object();
    /** Which actor each blocked thread is waiting for.  Guarded by GRAPH. */
    private static final Map<Thread, Actor> WAITING_FOR = new HashMap<>();

    public Actor() {
    }

    /** Starts a turn, or continues the current one if this thread holds it. */
    public void enter() {
        if (!lock.tryLock()) {
            Thread me = Thread.currentThread();
            synchronized (GRAPH) {
                // Follow owner -> what that owner waits for -> its owner ...
                // If the chain comes back to this thread, waiting would hang.
                // A blocked thread took its locks before it registered here,
                // so the walk sees who holds them.  Every waiting thread
                // checked before it blocked, so the graph has no cycle and
                // the walk ends; the bound is a guard.
                Actor actor = this;
                for (int steps = WAITING_FOR.size(); actor != null && steps >= 0; --steps) {
                    Thread holder = actor.lock.owner();
                    if (holder == null) {
                        break;
                    }
                    if (holder == me) {
                        throw new CycleException();
                    }
                    actor = WAITING_FOR.get(holder);
                }
                WAITING_FOR.put(me, this);
            }
            try {
                lock.lock();
            } finally {
                synchronized (GRAPH) {
                    WAITING_FOR.remove(me);
                }
            }
        }
    }

    /** Ends what the matching {@link #enter} started. */
    public void exit() {
        lock.unlock();
    }

    /** Exposes the owner, which ReentrantLock keeps but only shows subclasses. */
    private static final class Lock extends ReentrantLock {
        private static final long serialVersionUID = 1L;

        /** The holding thread, or null while free or being handed over. */
        Thread owner() {
            return getOwner();
        }
    }

    /**
     * Thrown by {@link #enter} when waiting would close a cycle of actors
     * waiting on each other.  A RuntimeException, like a Temper panic.
     */
    public static final class CycleException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CycleException() {
            super("actor call cycle");
        }
    }
}
