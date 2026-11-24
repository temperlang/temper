package host_failures_own_message;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostFailuresOwnMessageException extends RuntimeException implements Failure {
    protected HostFailuresOwnMessageException() {}
}
