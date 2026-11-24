using System;
using System.Collections.Generic;
using TemperLang.Core;

namespace ContentTags;

public class Quoting : IContextualAutoescapingAccumulator
{
    private bool inQuote;
    private readonly Buffer collector;

    public static Buffer NewCollector()
    {
        return new Buffer();
    }

    public static bool InitialState()
    {
        return false;
    }

    public static Propagator Propagator()
    {
        return new Propagator("'");
    }

    public static Picker Picker()
    {
        return new Picker(false);
    }

    public static Func<bool, bool, bool> SameStatePredicate()
    {
        return (bool a, bool b) => a == b;
    }

    public static bool MergeStates(bool a, bool b, Action<string, bool> callout)
    {
        if (a != b)
        {
            callout("branches end in different contexts", true);
        }
        return a;
    }

    public static void CheckEndState(bool inQuote, Action<string, bool> callout)
    {
        if (inQuote)
        {
            callout("ends inside quotes", true);
        }
    }

    public static string FromCollector(Buffer c)
    {
        return c.ToString();
    }

    public static void Enact(Mark m, Buffer c)
    {
        c.Mark(m);
    }

    public Quoting()
    {
        this.inQuote = false;
        this.collector = new Buffer();
    }

    public void AppendSafe(string x)
    {
        After after = ContentTagsGlobal.PropagateOver(Propagator(), this.inQuote, x, SameStatePredicate(), ContentTagsGlobal.Ignore);
        this.inQuote = after.StateAfter;
        {
            IReadOnlyList<IEffect> list = after.Effects;
            int length = list.Count;
            int i = 0;
            while (i < length)
            {
                IEffect e = Listed.Get(list, i);
                i = i + 1;
                e.EnactOn(this.collector);
            }
        }
    }

    public void Append(string x)
    {
        string escaped = Picker().EscaperFor(this.inQuote, ContentTagsGlobal.Ignore).ApplyString(x);
        this.collector.Append(escaped);
    }

    public string Accumulated
    {
        get
        {
            return this.collector.ToString();
        }
    }
}
