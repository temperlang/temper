namespace Modules.State;

public class Parts
{
    private readonly int count;

    public Parts(int count)
    {
        this.count = count;
    }

    public int Count
    {
        get
        {
            return this.count;
        }
    }
}
