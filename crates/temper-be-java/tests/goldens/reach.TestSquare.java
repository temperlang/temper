package reach;

final class TestSquare implements TestShape {
    public final int side;

    public TestSquare(int side) {
        this.side = side;
    }

    public int area() {
        return this.side * this.side;
    }

    public int getSide() {
        return this.side;
    }
}
