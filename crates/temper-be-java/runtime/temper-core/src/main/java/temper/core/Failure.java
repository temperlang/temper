package temper.core;

/**
 * Marks a Temper library's declared failures (RFC 164): each library's
 * base failure class, as {@code WherewithException}, extends
 * {@link RuntimeException} and implements this, and its failure types
 * extend that base. Host code catches those, never this, which isn't an
 * exception itself; generated code uses it only where it can't name a
 * failure type. A failure's message is {@link #getMessage()}, Temper's
 * {@code message}, and its cause is Java's own {@link #getCause()}: the
 * failure that was being handled where it was thrown, if any.
 */
public interface Failure {
    /**
     * What went wrong, for people: Temper's {@code message}.
     * @return the message
     */
    String getMessage();

    /**
     * What was being handled where this was thrown, as {@link Throwable}
     * has it.
     * @return the cause, or null
     */
    Throwable getCause();
}
