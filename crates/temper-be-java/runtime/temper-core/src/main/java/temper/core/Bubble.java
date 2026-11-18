package temper.core;

/**
 * A Temper bubble: a failure that Temper code declares (`throws Bubble`)
 * and that `orelse` handles.  Generated code catches only this, so any
 * other exception, whether a panic or a host failure, passes every
 * `orelse`.  Host code (a library's {@code @connected} code) bubbles by
 * throwing one, as {@code throw Core.bubble()} does.
 *
 * <p>Unchecked, as Temper's failures needn't be declared in Java, and
 * without a stack trace, which bubbles that are handled don't need.
 */
public class Bubble extends RuntimeException {
    private static final long serialVersionUID = 1L;

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
        super(message, cause, false, false);
    }
}
