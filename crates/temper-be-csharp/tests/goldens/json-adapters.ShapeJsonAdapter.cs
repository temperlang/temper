using TemperLang.Std.Json;

namespace JsonAdapters;

internal class ShapeJsonAdapter : IJsonAdapter<IShape>
{
    public ShapeJsonAdapter()
    {
    }

    public void EncodeToJson(IShape x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public IShape DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return IShape.DecodeFromJson(t, ic);
    }
}
