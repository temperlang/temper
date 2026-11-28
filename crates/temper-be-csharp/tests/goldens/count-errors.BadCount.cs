using System;

namespace CountErrors;

public sealed class BadCount : CountException
{
    private readonly string text;

    public BadCount(string text)
        : this(text, null)
    {
    }

    internal BadCount(string text, Exception? cause)
        : base(cause)
    {
        this.text = text;
    }

    public string Text
    {
        get
        {
            return this.text;
        }
    }

    public override string Message
    {
        get
        {
            return "bad count (text: " + this.text + ")";
        }
    }
}
