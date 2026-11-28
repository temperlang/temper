package host_declared_errors_several_libraries;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostDeclaredErrorsException extends RuntimeException implements Failure {
    protected HostDeclaredErrorsException() {}
}
