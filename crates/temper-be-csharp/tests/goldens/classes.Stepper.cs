using System;

namespace Classes;

public class Stepper : ILimited
{
    private readonly int limit;

    public Stepper(int limit)
    {
        this.limit = limit;
    }

    public int Limit
    {
        get
        {
            return this.limit;
        }
    }

    public int Next(int n)
    {
        if (n < this.limit)
        {
            return n + 1;
        }
        else
        {
            return this.TooFar();
        }
    }

    public int TooFar()
    {
        Environment.FailFast("panic");
        throw new InvalidOperationException();
    }
}
