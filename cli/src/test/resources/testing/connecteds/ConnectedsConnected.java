package connecteds;

import org.roaringbitmap.RoaringBitmap;

class ConnectedsConnected {
    public static Object newBitsetConnected() {
        return new RoaringBitmap();
    }

    public static void bitsetAdd(Object bitset, int i) {
        ((RoaringBitmap)bitset).add(i);
    }

    public static boolean bitsetContains(Object bitset, int i) {
        return ((RoaringBitmap)bitset).contains(i);
    }
}
