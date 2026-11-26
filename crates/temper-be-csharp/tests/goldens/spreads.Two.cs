namespace Spreads;

internal class Two<T>
{
    private readonly T first;
    private readonly T second;

    public Two(T first, T second)
    {
        this.first = first;
        this.second = second;
    }

    public T First
    {
        get
        {
            return this.first;
        }
    }

    public T Second
    {
        get
        {
            return this.second;
        }
    }
}
