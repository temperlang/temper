using System;

namespace CsharpFailures;

public sealed class Wrapped : CsharpFailuresException
{
    private readonly string label;
    private readonly Wrapped? first;

    public Wrapped(string label, Wrapped? first)
        : this(label, first, null)
    {
    }

    internal Wrapped(string label, Wrapped? first, Exception? cause)
        : base(cause)
    {
        this.label = label;
        this.first = first;
    }

    public string Label
    {
        get
        {
            return this.label;
        }
    }

    public Wrapped? First
    {
        get
        {
            return this.first;
        }
    }

    public override string Message
    {
        get
        {
            return "wrapped (label: " + this.label + ", first: " + (this.first == null ? "null" : "Wrapped") + ")";
        }
    }
}
