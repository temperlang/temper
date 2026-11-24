package host_failures_host_failure;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostFailuresHostFailureException extends RuntimeException implements Failure {
    protected HostFailuresHostFailureException() {}
}
