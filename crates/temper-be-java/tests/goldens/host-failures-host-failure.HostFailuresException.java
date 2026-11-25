package host_failures_host_failure;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostFailuresException extends RuntimeException implements Failure {
    protected HostFailuresException() {}
}
