namespace Namespaces.Shapes;

public class Box<T>
{
    private readonly T value;

    public Box(T value)
    {
        this.value = value;
    }

    public T Value
    {
        get
        {
            return this.value;
        }
    }
}
