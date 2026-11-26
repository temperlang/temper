using TemperLang.Std.Json;

namespace JsonAdapters;

internal class HelloJsonAdapter : IJsonAdapter<Hello>
{
    public HelloJsonAdapter()
    {
    }

    public void EncodeToJson(Hello x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public Hello DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Hello.DecodeFromJson(t, ic);
    }
}
