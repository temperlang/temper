package host_failures_cause;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostFailuresCauseException extends RuntimeException implements Failure {
    protected HostFailuresCauseException() {}
}
