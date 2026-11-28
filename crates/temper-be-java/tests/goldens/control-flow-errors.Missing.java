package errors;

@SuppressWarnings("serial")
final class Missing extends Problem {
    public Missing() {}

    public String getMessage() {
        return "missing";
    }
}
