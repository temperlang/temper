package bit_vector;

import java.util.BitSet;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class BitVectorGlobal {
    private BitVectorGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("bit_vector"));
        BitSet v = new BitSet(16);
        console.log("v[2]=" + Core.bitVectorGet(v, 2));
        console.log("v[3]=" + Core.bitVectorGet(v, 3));
        console.log("v[3] <- true");
        v.set(3, true);
        console.log("v[2]=" + Core.bitVectorGet(v, 2));
        console.log("v[3]=" + Core.bitVectorGet(v, 3));
        console.log("v[3] <- false");
        v.set(3, false);
        console.log("v[2]=" + Core.bitVectorGet(v, 2));
        console.log("v[3]=" + Core.bitVectorGet(v, 3));
        v.set(100, true);
        v.set(200, false);
        console.log("v[99]=" + Core.bitVectorGet(v, 99) + " v[100]=" + Core.bitVectorGet(v, 100) + " v[101]=" + Core.bitVectorGet(v, 101) + " v[200]=" + Core.bitVectorGet(v, 200) + " v[-1]=" + Core.bitVectorGet(v, -1));
        v.set(1000, true);
        console.log("v[999]=" + Core.bitVectorGet(v, 999) + " v[1000]=" + Core.bitVectorGet(v, 1000) + " v[100]=" + Core.bitVectorGet(v, 100));
        BitSet w = new BitSet(Math.max(-9, 0));
        w.set(1, true);
        console.log("w[0]=" + Core.bitVectorGet(w, 0) + " w[1]=" + Core.bitVectorGet(w, 1));
        console.log("before");
        v.set(-1, true);
        console.log("not reached");
    }
}
