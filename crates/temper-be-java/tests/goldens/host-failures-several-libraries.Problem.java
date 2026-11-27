package host_failures_several_libraries;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class Problem extends RuntimeException implements Failure {
    protected Problem() {}
}
