package declared_errors;

@SuppressWarnings("serial")
final class EmptyText extends SignException {
    public EmptyText() {}

    public String getMessage() {
        return "empty text";
    }
}
