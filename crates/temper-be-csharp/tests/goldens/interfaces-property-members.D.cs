namespace PropertyMembers;

internal class D<T>
    where T : class, II
{
    private readonly T thing;

    public D(T thing)
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
