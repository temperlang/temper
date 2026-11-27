package panic_causes_uncaught_failure;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class Problem extends RuntimeException implements Failure {
    protected Problem() {}
}
