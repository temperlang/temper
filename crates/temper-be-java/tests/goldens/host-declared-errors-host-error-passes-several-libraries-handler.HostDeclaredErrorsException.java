package host_declared_errors_host_error_passes_several_libraries_handler;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostDeclaredErrorsException extends RuntimeException implements Failure {
    protected HostDeclaredErrorsException() {}
}
