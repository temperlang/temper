package temper.core;

/**
 * What a generator's {@code get()}, Temper's {@code next()}, throws when
 * its body throws a declared failure, which is its cause; the generator
 * is done after. It has no fields. It has no stack trace unless
 * {@link Core#keepStackTraces} was called.
 */
@SuppressWarnings("serial")
public final class BrokenGenerator extends CoreException {
    public BrokenGenerator() {}

    @Override
    public String getMessage() {
        return "broken generator";
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return Core.keepsStackTraces() ? super.fillInStackTrace() : this;
    }
}
