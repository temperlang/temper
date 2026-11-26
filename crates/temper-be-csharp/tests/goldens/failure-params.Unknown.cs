using System;

namespace FailureParams;

internal sealed class Unknown : FailureParamsException
{
    private readonly string word;

    public Unknown(string word)
        : this(word, null)
    {
    }

    internal Unknown(string word, Exception? cause)
        : base(cause)
    {
        this.word = word;
    }

    public string Word
    {
        get
        {
            return this.word;
        }
    }

    public override string Message
    {
        get
        {
            return "unknown (word: " + this.word + ")";
        }
    }
}
