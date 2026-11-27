package test_panics;

@SuppressWarnings("serial")
final class Oops extends Problem {
    public Oops() {}

    public String getMessage() {
        return "oops";
    }
}
