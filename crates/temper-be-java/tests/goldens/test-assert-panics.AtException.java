package test_assert_panics;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class AtException extends RuntimeException implements Failure {
    protected AtException() {}
}
