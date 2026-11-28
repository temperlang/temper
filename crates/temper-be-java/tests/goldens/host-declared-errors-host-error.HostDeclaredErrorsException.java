package host_declared_errors_host_error;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostDeclaredErrorsException extends RuntimeException implements Failure {
    protected HostDeclaredErrorsException() {}
}
