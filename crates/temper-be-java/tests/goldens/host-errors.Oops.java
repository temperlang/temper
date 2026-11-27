package host_errors;

@SuppressWarnings("serial")
final class Oops extends Problem {
    public Oops() {}

    public String getMessage() {
        return "oops";
    }
}
