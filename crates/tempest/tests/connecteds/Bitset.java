package connecteds;

import org.roaringbitmap.RoaringBitmap;

/** Temper's connected Bitset class, with the members Temper would generate. */
public final class Bitset {
    private final RoaringBitmap roaring = new RoaringBitmap();

    public void add(int i) {
        roaring.add(i);
    }

    public boolean contains(int i) {
        return roaring.contains(i);
    }
}
