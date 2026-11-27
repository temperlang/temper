package test_panics;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class Problem extends RuntimeException implements Failure {
    protected Problem() {}
}
