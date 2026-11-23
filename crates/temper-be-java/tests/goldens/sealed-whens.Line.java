package sealed_whens;

final class Line implements Shape {
    public final int size;

    public Line(int size) {
        this.size = size;
    }

    public int getSize() {
        return this.size;
    }
}
