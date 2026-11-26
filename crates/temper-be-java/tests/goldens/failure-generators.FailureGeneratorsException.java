package failure_generators;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class FailureGeneratorsException extends RuntimeException implements Failure {
    protected FailureGeneratorsException() {}
}
