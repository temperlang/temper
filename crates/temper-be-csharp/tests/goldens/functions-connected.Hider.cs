namespace Work;

public class Hider
{
    private readonly Hidden hidden;

    public Hider(int i)
    {
        this.hidden = new Hidden(i);
    }

    public int Plus(int j)
    {
        return WorkGlobal.Sum(this.hidden.I, j);
    }

    public int Times(int j)
    {
        return WorkGlobal.Prod(this.hidden, j);
    }
}
