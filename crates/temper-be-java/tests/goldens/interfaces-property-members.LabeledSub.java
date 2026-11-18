package property_members;

final class LabeledSub implements Labeled<String, C> {
    public final C held;

    public LabeledSub(C held) {
        this.held = held;
    }

    public String label(String l) {
        return l + ": " + this.held.x;
    }

    public C getHeld() {
        return this.held;
    }
}
