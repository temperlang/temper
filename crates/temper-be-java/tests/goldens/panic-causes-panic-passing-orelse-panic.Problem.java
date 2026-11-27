package panic_causes_panic_passing_orelse_panic;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class Problem extends RuntimeException implements Failure {
    protected Problem() {}
}
