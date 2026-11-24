package host_failures_own_message;

@SuppressWarnings("serial")
final class Expired extends HostFailuresOwnMessageException {
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
