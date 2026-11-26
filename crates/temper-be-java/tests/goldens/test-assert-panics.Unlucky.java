package test_assert_panics;

@SuppressWarnings("serial")
final class Unlucky extends AtException {
    public Unlucky() {}

    public String getMessage() {
        return "unlucky";
    }
}
