package host_failures_panic_passes_catch;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostFailuresException extends RuntimeException implements Failure {
    protected HostFailuresException() {}
}
