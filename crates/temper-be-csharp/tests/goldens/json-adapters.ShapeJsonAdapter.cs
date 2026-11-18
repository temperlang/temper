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

    public IShape DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return IShape.DecodeFromJson(t, ic);
    }
}
