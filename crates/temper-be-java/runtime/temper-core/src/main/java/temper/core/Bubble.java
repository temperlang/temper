package temper.core;

/**
 * A Temper bubble: a failure that Temper code declares (`throws Bubble`)
 * and that `orelse` handles.  Generated code catches only this, so any
 * other exception, whether a panic or a host failure, passes every
 * `orelse`.  Host code (a library's {@code @connected} code) bubbles by
 * throwing one, as {@code throw Core.bubble()} does.
 *
 * <p>Unchecked, as Temper's failures needn't be declared in Java, and
 * without a stack trace, which bubbles that are handled don't need,
 * unless {@link #keepStackTraces} was called, as a program's generated
 * main method does, so one that escapes says where it came from.
 */
public class Bubble extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private static volatile boolean stackTraces = false;

    /**
     * Gives bubbles made from now on stack traces, at a cost to each: a
     * program's main method calls it, and so may code that wants to see
     * where an uncaught bubble came from.
     */
    public static void keepStackTraces() {
        stackTraces = true;
    }

    /** Whether failures made now get stack traces. */
    static boolean keepsStackTraces() {
        return stackTraces;
    }

    /** A bubble without a message. */
    public Bubble() {
        this(null, null);
    }

    /**
     * A bubble that says why.
     * @param message why it bubbled
     */
    public Bubble(String message) {
        this(message, null);
    }

    /**
     * A bubble for a host failure.
     * @param message why it bubbled
     * @param cause the host's exception
     */
    public Bubble(String message, Throwable cause) {
        super(message, cause, false, stackTraces);
    }
}
