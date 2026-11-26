package temper.core;

/**
 * What awaiting a broken promise throws. It has no fields: a broken
 * promise doesn't say why, though what broke it, a host's exception, is
 * its cause. Like a {@link Bubble}, it has no stack trace unless
 * {@link Bubble#keepStackTraces} was called.
 */
@SuppressWarnings("serial")
public final class BrokenPromise extends CoreException {
    public BrokenPromise() {}

    @Override
    public String getMessage() {
        return "broken promise";
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return Bubble.keepsStackTraces() ? super.fillInStackTrace() : this;
    }
}
