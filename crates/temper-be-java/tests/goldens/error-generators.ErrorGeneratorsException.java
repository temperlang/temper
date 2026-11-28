package error_generators;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class ErrorGeneratorsException extends RuntimeException implements Failure {
    protected ErrorGeneratorsException() {}
}
