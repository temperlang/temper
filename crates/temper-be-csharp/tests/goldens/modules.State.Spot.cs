namespace Modules.State;

public class Spot
{
    private readonly int x;
    private readonly int y;

    public Spot(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    public int X
    {
        get
        {
            return this.x;
        }
    }

    public int Y
    {
        get
        {
            return this.y;
        }
    }
}
