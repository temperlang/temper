package namespaces.shapes;

public final class Square implements Shape {
    public final int side;

    public Square(int side) {
        this.side = side;
    }

    public int area() {
        return this.side * this.side;
    }

    public static Square sized(int side) {
        return new Square(side);
    }

    public int getSide() {
        return this.side;
    }
}
