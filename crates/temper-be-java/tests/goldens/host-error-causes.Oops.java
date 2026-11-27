package host_error_causes;

@SuppressWarnings("serial")
public final class Oops extends OopsException {
    public Oops() {}

    public String getMessage() {
        return "oops";
    }
}
