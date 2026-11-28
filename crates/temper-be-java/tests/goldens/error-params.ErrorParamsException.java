package error_params;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class ErrorParamsException extends RuntimeException implements Failure {
    protected ErrorParamsException() {}
}
