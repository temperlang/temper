package count_failures;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class CountException extends RuntimeException implements Failure {
    protected CountException() {}
}
