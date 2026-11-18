namespace Modules.State;

public class Engine
{
    private readonly int n;

    public Engine(int n)
    {
        this.n = n;
    }

    public int N
    {
        get
        {
            return this.n;
        }
    }
}
