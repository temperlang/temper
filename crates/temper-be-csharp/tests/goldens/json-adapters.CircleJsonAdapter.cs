using TemperLang.Std.Json;

namespace JsonAdapters;

internal class CircleJsonAdapter : IJsonAdapter<Circle>
{
    public CircleJsonAdapter()
    {
    }

    public void EncodeToJson(Circle x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    public Circle DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Circle.DecodeFromJson(t, ic);
    }
}
