package errors_interop;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class ErrorsInteropException extends RuntimeException implements Failure {
    protected ErrorsInteropException() {}
}
