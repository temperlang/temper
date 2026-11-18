using TemperLang.Std.Json;

namespace JsonAdapters;

internal class PointJsonAdapter : IJsonAdapter<Point>
{
    public PointJsonAdapter()
    {
    }

    public void EncodeToJson(Point x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    public Point DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Point.DecodeFromJson(t, ic);
    }
}
