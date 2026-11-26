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
        Core.bitVectorSet(v, 3, true);
        console.log("v[2]=" + Core.bitVectorGet(v, 2));
        console.log("v[3]=" + Core.bitVectorGet(v, 3));
        console.log("v[3] <- false");
        Core.bitVectorSet(v, 3, false);
        console.log("v[2]=" + Core.bitVectorGet(v, 2));
        console.log("v[3]=" + Core.bitVectorGet(v, 3));
        Core.bitVectorSet(v, 100, true);
        Core.bitVectorSet(v, 200, false);
        console.log("v[99]=" + Core.bitVectorGet(v, 99) + " v[100]=" + Core.bitVectorGet(v, 100) + " v[101]=" + Core.bitVectorGet(v, 101) + " v[200]=" + Core.bitVectorGet(v, 200) + " v[-1]=" + Core.bitVectorGet(v, -1));
        Core.bitVectorSet(v, 1000, true);
        console.log("v[999]=" + Core.bitVectorGet(v, 999) + " v[1000]=" + Core.bitVectorGet(v, 1000) + " v[100]=" + Core.bitVectorGet(v, 100));
        BitSet u = new BitSet(0);
        Core.bitVectorSet(u, 8, true);
        Core.bitVectorSet(u, 32, true);
        Core.bitVectorSet(u, 64, true);
        console.log(Core.bitVectorGet(u, 7) + " " + Core.bitVectorGet(u, 8) + " " + Core.bitVectorGet(u, 9) + " " + Core.bitVectorGet(u, 31) + " " + Core.bitVectorGet(u, 32) + " " + Core.bitVectorGet(u, 33) + " " + Core.bitVectorGet(u, 63) + " " + Core.bitVectorGet(u, 64) + " " + Core.bitVectorGet(u, 65));
        Core.bitVectorSet(v, 1000, false);
        console.log("v[1000]=" + Core.bitVectorGet(v, 1000) + " v[999]=" + Core.bitVectorGet(v, 999));
        BitSet w = new BitSet(Math.max(-9, 0));
        Core.bitVectorSet(w, 1, true);
        console.log("w[0]=" + Core.bitVectorGet(w, 0) + " w[1]=" + Core.bitVectorGet(w, 1));
        console.log("before");
        Core.bitVectorSet(v, -1, true);
        console.log("not reached");
    }
}
