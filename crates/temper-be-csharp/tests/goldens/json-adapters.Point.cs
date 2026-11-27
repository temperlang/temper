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

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static Point DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj;
        if (t is JsonObject)
        {
            obj = (JsonObject)t;
        }
        else
        {
            throw new JsonShapeException("expected Point JSON object");
        }
        IJsonSyntaxTree xTree = obj.PropertyValueOrThrow("x");
        IJsonNumeric xJson;
        if (xTree is IJsonNumeric)
        {
            xJson = (IJsonNumeric)xTree;
        }
        else
        {
            throw new JsonShapeException("expected Int for x");
        }
        int x;
        try
        {
            x = xJson.AsInt32();
        }
        catch (ParseError failure)
        {
            throw Core.WithCause(new JsonShapeException("expected Int for x"), failure);
        }
        IJsonSyntaxTree yTree = obj.PropertyValueOrThrow("y");
        IJsonNumeric yJson;
        if (yTree is IJsonNumeric)
        {
            yJson = (IJsonNumeric)yTree;
        }
        else
        {
            throw new JsonShapeException("expected Int for y");
        }
        int y;
        try
        {
            y = yJson.AsInt32();
        }
        catch (ParseError failure2)
        {
            throw Core.WithCause(new JsonShapeException("expected Int for y"), failure2);
        }
        return new Point(x, y);
    }

    public static IJsonAdapter<Point> JsonAdapter()
    {
        return new PointJsonAdapter();
    }
}
