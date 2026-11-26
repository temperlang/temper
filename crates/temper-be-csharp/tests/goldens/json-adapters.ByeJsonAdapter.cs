using TemperLang.Std.Json;

namespace JsonAdapters;

internal class ByeJsonAdapter : IJsonAdapter<Bye>
{
    public ByeJsonAdapter()
    {
    }

    public void EncodeToJson(Bye x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public Bye DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Bye.DecodeFromJson(t, ic);
    }
}
