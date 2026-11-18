package classes;

final class Loose {
    public final int n;

    public Loose(int n) {
        this.n = n;
    }

    public boolean eq(Loose other) {
        if (this.n == other.n) {
            return true;
        } else {
            return this.n == -other.n;
        }
    }

    public boolean mirrors(Loose other) {
        if (other.eq(this)) {
            return this.eq(other);
        } else {
            return false;
        }
    }

    public int getN() {
        return this.n;
    }
}
