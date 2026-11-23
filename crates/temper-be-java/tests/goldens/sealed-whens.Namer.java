package sealed_whens;

final class Namer {
    public Namer() {}

    public String name(Shape s) {
        if (s instanceof Dot) {
            return "a dot";
        } else if (s instanceof Line) {
            return "a line";
        } else {
            throw new AssertionError("panic");
        }
    }
}
