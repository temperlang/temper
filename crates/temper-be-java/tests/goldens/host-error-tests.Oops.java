package host_error_tests;

@SuppressWarnings("serial")
public final class Oops extends OopsException {
    public Oops() {}

    public String getMessage() {
        return "oops";
    }
}
