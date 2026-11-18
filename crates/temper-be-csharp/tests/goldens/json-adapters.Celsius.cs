using TemperLang.Std.Json;

namespace JsonAdapters;

internal class Celsius
{
    private readonly int degrees;

    public Celsius(int degrees)
    {
        this.degrees = degrees;
    }

    public int Degrees
    {
        get
        {
            return this.degrees;
        }
    }

    public void EncodeToJson(IJsonProducer p)
    {
        p.Int32_Value(this.degrees);
    }

    public static Celsius DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return new Celsius(((IJsonNumeric)t).AsInt32());
    }

    public static IJsonAdapter<Celsius> JsonAdapter()
    {
        return new CelsiusJsonAdapter();
    }
}
