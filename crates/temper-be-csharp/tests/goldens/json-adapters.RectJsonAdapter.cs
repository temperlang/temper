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

    public Rect DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Rect.DecodeFromJson(t, ic);
    }
}
