namespace Generics;

public class Counter
{
    private int n;

    public Counter(int n)
    {
        this.n = n;
    }

    public int N
    {
        get
        {
            return this.n;
        }
        set
        {
            this.n = value;
        }
    }
}
