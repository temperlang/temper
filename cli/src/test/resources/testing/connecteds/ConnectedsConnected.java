package connecteds;

import org.roaringbitmap.RoaringBitmap;

class ConnectedsConnected {
    public static Bitset newBitset() {
        return new Bitset(new RoaringBitmap());
    }

    public static void bitsetAdd(Bitset bitset, int i) {
        ((RoaringBitmap)bitset.getInternal()).add(i);
    }

    public static boolean bitsetContains(Bitset bitset, int i) {
        return ((RoaringBitmap)bitset.getInternal()).contains(i);
    }
}
