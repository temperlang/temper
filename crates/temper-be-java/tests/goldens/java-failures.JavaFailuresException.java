package java_failures;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class JavaFailuresException extends RuntimeException implements Failure {
    protected JavaFailuresException() {}
}
