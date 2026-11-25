using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonAdapters;

internal class Point
{
    private readonly int x;
    private readonly int y;

    public Point(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    public int X
    {
        get
        {
            return this.x;
        }
    }

    public int Y
    {
        get
        {
            return this.y;
        }
    }

    public void EncodeToJson(IJsonProducer p)
    {
        p.StartObject();
        p.ObjectKey("x");
        p.Int32_Value(this.x);
        p.ObjectKey("y");
        p.Int32_Value(this.y);
        p.EndObject();
    }

    public static Point DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj = t is JsonObject ? (JsonObject)t : Core.Bubble<JsonObject>();
        IJsonSyntaxTree xTree = obj.PropertyValueOrBubble("x");
        IJsonNumeric xJson = xTree is IJsonNumeric ? (IJsonNumeric)xTree : Core.Bubble<IJsonNumeric>();
        int x = xJson.AsInt32();
        IJsonSyntaxTree yTree = obj.PropertyValueOrBubble("y");
        IJsonNumeric yJson = yTree is IJsonNumeric ? (IJsonNumeric)yTree : Core.Bubble<IJsonNumeric>();
        int y = yJson.AsInt32();
        return new Point(x, y);
    }

    public static IJsonAdapter<Point> JsonAdapter()
    {
        return new PointJsonAdapter();
    }
}
