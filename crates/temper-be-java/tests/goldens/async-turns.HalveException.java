package async_turns;

import temper.core.Failure;

@SuppressWarnings("serial")
public abstract class HalveException extends RuntimeException implements Failure {
    protected HalveException() {}
}
