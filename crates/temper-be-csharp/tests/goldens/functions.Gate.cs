using System;

namespace Functions;

internal class Gate
{
    private Func<int, int>? check;

    public Gate()
    {
        this.check = null;
    }

    public Func<int, int>? Check
    {
        get
        {
            return this.check;
        }
        set
        {
            this.check = value;
        }
    }

    public bool Allows(int n)
    {
        Func<int, int>? c = this.check;
        if (c == null)
        {
            return true;
        }
        else
        {
            return c(n) > 0;
        }
    }

    public int Passed(int n)
    {
        Func<int, int>? c = this.check;
        if (c != null)
        {
            return c(n);
        }
        else
        {
            return n;
        }
    }
}
