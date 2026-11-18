using System.Collections.Generic;

namespace PrivateMethod;

internal class Tally
{
    private readonly int depth;
    private readonly int size;

    public Tally(int size, Tally? parent, IReadOnlyList<Tally> children)
    {
        this.depth = parent == null ? 0 : parent.depth + 1;
        int sum = size;
        {
            int i = 0;
            while (i < children.Count)
            {
                sum = sum + children[i].size;
                i = i + 1;
            }
        }
        this.size = sum + this.depth;
    }

    public int Depth
    {
        get
        {
            return this.depth;
        }
    }

    public int Total
    {
        get
        {
            return this.size;
        }
    }
}
