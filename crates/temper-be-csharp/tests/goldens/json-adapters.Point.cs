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
        JsonObject obj = Core.Cast<JsonObject>(t);
        IJsonNumeric xJson = Core.Cast<IJsonNumeric>(obj.PropertyValueOrBubble("x"));
        int x = xJson.AsInt32();
        IJsonNumeric yJson = Core.Cast<IJsonNumeric>(obj.PropertyValueOrBubble("y"));
        int y = yJson.AsInt32();
        return new Point(x, y);
    }

    public static IJsonAdapter<Point> JsonAdapter()
    {
        return new PointJsonAdapter();
    }
}
