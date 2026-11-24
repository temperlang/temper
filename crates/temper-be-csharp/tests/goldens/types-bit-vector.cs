using System;
using System.Collections;
using TemperLang.Core;

namespace BitVector;

public static class BitVectorGlobal
{
    static BitVectorGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("BitVector");
        BitArray v = new BitArray(16);
        console.Log("v[2]=" + (Core.BitGet(v, 2) ? "true" : "false"));
        console.Log("v[3]=" + (Core.BitGet(v, 3) ? "true" : "false"));
        console.Log("v[3] <- true");
        Core.BitSet(v, 3, true);
        console.Log("v[2]=" + (Core.BitGet(v, 2) ? "true" : "false"));
        console.Log("v[3]=" + (Core.BitGet(v, 3) ? "true" : "false"));
        console.Log("v[3] <- false");
        Core.BitSet(v, 3, false);
        console.Log("v[2]=" + (Core.BitGet(v, 2) ? "true" : "false"));
        console.Log("v[3]=" + (Core.BitGet(v, 3) ? "true" : "false"));
        Core.BitSet(v, 100, true);
        Core.BitSet(v, 200, false);
        console.Log("v[99]=" + (Core.BitGet(v, 99) ? "true" : "false") + " v[100]=" + (Core.BitGet(v, 100) ? "true" : "false") + " v[101]=" + (Core.BitGet(v, 101) ? "true" : "false") + " v[200]=" + (Core.BitGet(v, 200) ? "true" : "false") + " v[-1]=" + (Core.BitGet(v, -1) ? "true" : "false"));
        Core.BitSet(v, 1000, true);
        console.Log("v[999]=" + (Core.BitGet(v, 999) ? "true" : "false") + " v[1000]=" + (Core.BitGet(v, 1000) ? "true" : "false") + " v[100]=" + (Core.BitGet(v, 100) ? "true" : "false"));
        BitArray u = new BitArray(0);
        Core.BitSet(u, 8, true);
        Core.BitSet(u, 32, true);
        Core.BitSet(u, 64, true);
        console.Log((Core.BitGet(u, 7) ? "true" : "false") + " " + (Core.BitGet(u, 8) ? "true" : "false") + " " + (Core.BitGet(u, 9) ? "true" : "false") + " " + (Core.BitGet(u, 31) ? "true" : "false") + " " + (Core.BitGet(u, 32) ? "true" : "false") + " " + (Core.BitGet(u, 33) ? "true" : "false") + " " + (Core.BitGet(u, 63) ? "true" : "false") + " " + (Core.BitGet(u, 64) ? "true" : "false") + " " + (Core.BitGet(u, 65) ? "true" : "false"));
        Core.BitSet(v, 1000, false);
        console.Log("v[1000]=" + (Core.BitGet(v, 1000) ? "true" : "false") + " v[999]=" + (Core.BitGet(v, 999) ? "true" : "false"));
        BitArray w = new BitArray(Math.Max(-9, 0));
        Core.BitSet(w, 1, true);
        console.Log("w[0]=" + (Core.BitGet(w, 0) ? "true" : "false") + " w[1]=" + (Core.BitGet(w, 1) ? "true" : "false"));
        console.Log("before");
        Core.BitSet(v, -1, true);
        console.Log("not reached");
    }
}
