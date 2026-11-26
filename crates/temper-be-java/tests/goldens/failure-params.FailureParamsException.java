package failure_params;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class FailureParamsException extends RuntimeException implements Failure {
    protected FailureParamsException() {}
}
