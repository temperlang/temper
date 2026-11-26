package spreads;

final class Square implements Shape {
    public final int side;

    public Square(int side) {
        this.side = side;
    }

    public int getSide() {
        return this.side;
    }
}
