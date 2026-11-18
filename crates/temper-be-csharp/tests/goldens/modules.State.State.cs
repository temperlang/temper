namespace Modules.State;

public class State
{
    private readonly int id;

    public State(int id)
    {
        this.id = id;
    }

    public int Id
    {
        get
        {
            return this.id;
        }
    }
}
