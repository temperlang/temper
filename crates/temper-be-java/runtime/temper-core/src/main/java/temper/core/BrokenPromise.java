package temper.core;

/**
 * What awaiting a broken promise throws. It has no fields: a broken
 * promise doesn't say why, though what broke it, a host's exception, is
 * its cause. It has no stack trace unless {@link Core#keepStackTraces}
 * was called.
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
        return Core.keepsStackTraces() ? super.fillInStackTrace() : this;
    }
}
