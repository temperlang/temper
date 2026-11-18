package classes;

final class Counter {
    public int count;
    private int hidden;

    public Counter() {
        this.count = 0;
        this.hidden = 0;
    }

    public int getDoubled() {
        return this.count * 2;
    }

    public void setDoubled(int value) {
        this.count = value - this.count;
    }

    public void setAdded(int value) {
        this.count = this.count + value;
    }

    public Counter bump() {
        this.count = this.count + 1;
        this.hidden = this.hidden + 1;
        return this;
    }

    public boolean same(Counter other) {
        return this.hidden == other.hidden;
    }

    public static Counter zero() {
        return new Counter();
    }

    public int getCount() {
        return this.count;
    }

    public void setCount(int newCount) {
        this.count = newCount;
    }
}
