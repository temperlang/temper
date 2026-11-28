package declared_errors;

import temper.core.Failure;

@SuppressWarnings("serial")
abstract class DeclaredErrorsException extends RuntimeException implements Failure {
    protected DeclaredErrorsException() {}
}
