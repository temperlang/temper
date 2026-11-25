package host_failures_panic_passes_several_libraries_handler;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostFailuresException extends RuntimeException implements Failure {
    protected HostFailuresException() {}
}
