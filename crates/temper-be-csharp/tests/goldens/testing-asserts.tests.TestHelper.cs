namespace Asserts;

internal class TestHelper
{
    private readonly int i;
    private readonly int j;

    public TestHelper(int i, int j)
    {
        this.i = i;
        this.j = j;
    }

    public int I
    {
        get
        {
            return this.i;
        }
    }

    public int J
    {
        get
        {
            return this.j;
        }
    }
}
