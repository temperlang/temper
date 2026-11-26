using TemperLang.Std.Json;

namespace JsonAdapters;

internal class CelsiusJsonAdapter : IJsonAdapter<Celsius>
{
    public CelsiusJsonAdapter()
    {
    }

    public void EncodeToJson(Celsius x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public Celsius DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Celsius.DecodeFromJson(t, ic);
    }
}
