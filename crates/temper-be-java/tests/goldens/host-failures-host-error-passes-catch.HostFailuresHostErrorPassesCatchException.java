package host_failures_host_error_passes_catch;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HostFailuresHostErrorPassesCatchException extends RuntimeException implements Failure {
    protected HostFailuresHostErrorPassesCatchException() {}
}
