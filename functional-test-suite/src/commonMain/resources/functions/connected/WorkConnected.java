package work;

class WorkConnected {
    static int sum(int i, int j, Integer bonus) {
        int b = bonus != null ? bonus : 0;
        return i + j + b;
    }

    static int prod(Hidden hidden, int j) {
        return new Support().prod(hidden.i, j);
    }
}
