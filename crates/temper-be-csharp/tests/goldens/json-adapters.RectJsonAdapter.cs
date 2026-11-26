using TemperLang.Std.Json;

namespace JsonAdapters;

internal class RectJsonAdapter : IJsonAdapter<Rect>
{
    public RectJsonAdapter()
    {
    }

    public void EncodeToJson(Rect x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public Rect DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Rect.DecodeFromJson(t, ic);
    }
}
