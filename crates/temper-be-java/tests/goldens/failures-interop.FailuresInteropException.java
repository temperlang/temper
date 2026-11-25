package failures_interop;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class FailuresInteropException extends RuntimeException implements Failure {
    protected FailuresInteropException() {}
}
