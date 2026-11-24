package bit_vector_negative_false;

import java.util.BitSet;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class BitVectorNegativeFalseGlobal {
    private BitVectorNegativeFalseGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("bit_vector_negative_false"));
        BitSet v = new BitSet(2);
        Core.bitVectorSet(v, 5, false);
        console.log("v[5]=" + Core.bitVectorGet(v, 5));
        console.log("before");
        Core.bitVectorSet(v, -1, false);
        console.log("not reached");
    }
}
