namespace NullableTypeArgs;

internal class Tag : IShown
{
    private readonly string tag;

    public Tag(string tag)
    {
        this.tag = tag;
    }

    public string Tag_
    {
        get
        {
            return this.tag;
        }
    }

    public string Shown
    {
        get
        {
            return this.tag;
        }
    }
}
