package host_declared_errors_own_message;

@SuppressWarnings("serial")
final class Expired extends HostDeclaredErrorsException {
    public final int days;

    public Expired(int days) {
        this.days = days;
    }

    public String getMessage() {
        return "expired " + this.days + " days ago";
    }

    public int getDays() {
        return this.days;
    }
}
