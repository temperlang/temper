using System.Collections;
using TemperLang.Core;

namespace BitVectorNegativeFalse;

public static class BitVectorNegativeFalseGlobal
{
    static BitVectorNegativeFalseGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("BitVectorNegativeFalse");
        BitArray v = new BitArray(2);
        Core.BitSet(v, 5, false);
        console.Log("v[5]=" + (Core.BitGet(v, 5) ? "true" : "false"));
        console.Log("before");
        Core.BitSet(v, -1, false);
        console.Log("not reached");
    }
}
