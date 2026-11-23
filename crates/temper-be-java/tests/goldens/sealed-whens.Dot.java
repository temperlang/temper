package sealed_whens;

final class Dot implements Shape {
    public final int size;

    public Dot(int size) {
        this.size = size;
    }

    public int getSize() {
        return this.size;
    }
}
