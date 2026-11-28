package host_declared_errors_own_message;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostDeclaredErrorsException extends RuntimeException implements Failure {
    protected HostDeclaredErrorsException() {}
}
