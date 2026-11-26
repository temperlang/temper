package namespaces;

import namespaces.shapes.Shape;

final class Circle implements Shape {
    public final int r;

    public Circle(int r) {
        this.r = r;
    }

    public int area() {
        return 3 * this.r * this.r;
    }

    public int getR() {
        return this.r;
    }
}
