using System.Collections.Generic;

namespace PropertyMembers;

internal class LabeledSub : ILabeled<string, C>
{
    private readonly C held;

    public LabeledSub(C held)
    {
        this.held = held;
    }

    public C Held
    {
        get
        {
            return this.held;
        }
    }

    public string Label(string l)
    {
        return l + ": " + this.held.X;
    }

    public IReadOnlyList<C> HeldAnd(C other)
    {
        return IHolder<C>.HeldAndDefault(this, other);
    }
}
