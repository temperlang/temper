package failures;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class FailuresException extends RuntimeException implements Failure {
    protected FailuresException() {}
}
