namespace PropertyMembers;

internal class SSub<T> : ISi<T>
    where T : class, II
{
    private readonly T thing;

    public SSub(T thing)
    {
        this.thing = thing;
    }

    public T Thing
    {
        get
        {
            return this.thing;
        }
    }
}
