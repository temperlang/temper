package host_declared_errors_panic_passes_catch;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostDeclaredErrorsException extends RuntimeException implements Failure {
    protected HostDeclaredErrorsException() {}
}
