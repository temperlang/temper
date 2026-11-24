package failures;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class FailuresException extends RuntimeException implements Failure {
    protected FailuresException() {}
}
