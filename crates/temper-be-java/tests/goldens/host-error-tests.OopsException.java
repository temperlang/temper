package host_error_tests;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class OopsException extends RuntimeException implements Failure {
    protected OopsException() {}
}
