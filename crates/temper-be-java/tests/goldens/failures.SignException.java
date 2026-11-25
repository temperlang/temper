package failures;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class SignException extends RuntimeException implements Failure {
    protected SignException() {}
}
