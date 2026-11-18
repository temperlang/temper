using System.Collections.Generic;
using TemperLang.Core;

namespace PropertyMembers;

public static class PropertyMembersGlobal
{
    internal static IT LeastX<IT>(IT a, IT b)
        where IT : II
    {
        if (StringUtil.CompareStringsByCodePoint(((II)a).X, ((II)b).X) < 0)
        {
            return a;
        }
        else
        {
            return b;
        }
    }

    static PropertyMembersGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PropertyMembers");
        C a = new C("foo");
        C b = new C("bar");
        console.Log("leastX of " + a.X + " and " + b.X + " is " + LeastX(a, b).X + ".");
        II i = a;
        console.Log("As an I, a.x is " + i.X + ".");
        D<II> di = new D<II>(a);
        D<IJ> dj = new D<IJ>(a);
        console.Log("Generically, x is " + di.Thing.X + ".");
        console.Log("Genericallier, x is still " + ((II)dj.Thing).X + ".");
        ISi<C> si = new SSub<C>(a);
        console.Log("Sealed thing is " + si.Thing.X + ".");
        ILabeled<string, C> labeled = new LabeledSub(a);
        IReadOnlyList<C> heldAnd = ((IHolder<C>)labeled).HeldAnd(b);
        console.Log("Labeled thing is " + labeled.Label("first") + ".");
        console.Log("Held are " + ((IHolder<C>)labeled).Held.X + " and " + heldAnd[1].X + ".");
        IHigh high1 = new Low1(11);
        IHigh high1b = new Low1(12);
        IHigh high1c = new Low1(12);
        IHigh high2 = new Low2("two");
        IHigh high2b = new Low2("to be");
        IHigh high2c = new Low2("to be");
        IHigh high3 = new Low3();
        IHigh high3b = new Low3();
        IReadOnlyList<KeyValuePair<IHigh, IHigh>> pairs = Listed.CreateReadOnlyList<KeyValuePair<IHigh, IHigh>>(new KeyValuePair<IHigh, IHigh>(high1, high1b), new KeyValuePair<IHigh, IHigh>(high1b, high1c), new KeyValuePair<IHigh, IHigh>(high1, high2), new KeyValuePair<IHigh, IHigh>(high2, high2b), new KeyValuePair<IHigh, IHigh>(high2b, high2c), new KeyValuePair<IHigh, IHigh>(high1, high3), new KeyValuePair<IHigh, IHigh>(high3, high3b));
        {
            int length = pairs.Count;
            int i2 = 0;
            while (i2 < length)
            {
                KeyValuePair<IHigh, IHigh> pair = pairs[i2];
                i2 = i2 + 1;
                console.Log(pair.Key.ToString() + " == " + pair.Value.ToString() + ": " + (pair.Key.Eq(pair.Value) ? "true" : "false"));
            }
        }
    }
}
