using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonAdapters;

internal class Rect : IShape
{
    private readonly int width;
    private readonly int height;

    public Rect(int width, int height)
    {
        this.width = width;
        this.height = height;
    }

    public int Width
    {
        get
        {
            return this.width;
        }
    }

    public int Height
    {
        get
        {
            return this.height;
        }
    }

    public void EncodeToJson(IJsonProducer p)
    {
        p.StartObject();
        p.ObjectKey("width");
        p.Int32_Value(this.width);
        p.ObjectKey("height");
        p.Int32_Value(this.height);
        p.EndObject();
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static Rect DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj;
        if (t is JsonObject)
        {
            obj = (JsonObject)t;
        }
        else
        {
            throw new JsonShapeException("expected Rect JSON object");
        }
        IJsonSyntaxTree widthTree = obj.PropertyValueOrBubble("width");
        IJsonNumeric widthJson;
        if (widthTree is IJsonNumeric)
        {
            widthJson = (IJsonNumeric)widthTree;
        }
        else
        {
            throw new JsonShapeException("expected Int for width");
        }
        int width;
        try
        {
            width = widthJson.AsInt32();
        }
        catch (ParseError failure)
        {
            throw Core.WithCause(new JsonShapeException("expected Int for width"), failure);
        }
        IJsonSyntaxTree heightTree = obj.PropertyValueOrBubble("height");
        IJsonNumeric heightJson;
        if (heightTree is IJsonNumeric)
        {
            heightJson = (IJsonNumeric)heightTree;
        }
        else
        {
            throw new JsonShapeException("expected Int for height");
        }
        int height;
        try
        {
            height = heightJson.AsInt32();
        }
        catch (ParseError failure2)
        {
            throw Core.WithCause(new JsonShapeException("expected Int for height"), failure2);
        }
        return new Rect(width, height);
    }

    public static IJsonAdapter<Rect> JsonAdapter()
    {
        return new RectJsonAdapter();
    }
}
