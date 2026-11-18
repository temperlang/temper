package write_back_through_field;

final class Pong {
    public Pong() {}

    public void pong(Ping a) {
        a.n = a.n + 1;
    }

    public int bump(Ping a, int by) {
        a.n = a.n + by;
        return a.n;
    }
}
