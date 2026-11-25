using System;

namespace CsharpFailures;

public sealed class BadSign : SignException
{
    private readonly string text;

    public BadSign(string text)
        : this(text, null)
    {
    }

    internal BadSign(string text, Exception? cause)
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
            return "bad sign (text: " + this.text + ")";
        }
    }
}
