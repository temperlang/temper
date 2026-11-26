package temper.core;

/**
 * The root of core's own declared failures, {@link MissingKey},
 * {@link ParseError}, {@link InvalidCodePoint}, and {@link BrokenPromise},
 * as each library's roots are its failures' (Temper's {@code CoreError}).
 */
@SuppressWarnings("serial")
public abstract class CoreException extends RuntimeException implements Failure {
    protected CoreException() {}
}
