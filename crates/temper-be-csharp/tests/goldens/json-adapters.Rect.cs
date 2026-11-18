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

    public static Rect DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj = (JsonObject)t;
        IJsonNumeric widthJson = (IJsonNumeric)obj.PropertyValueOrBubble("width");
        int width = widthJson.AsInt32();
        IJsonNumeric heightJson = (IJsonNumeric)obj.PropertyValueOrBubble("height");
        int height = heightJson.AsInt32();
        return new Rect(width, height);
    }

    public static IJsonAdapter<Rect> JsonAdapter()
    {
        return new RectJsonAdapter();
    }
}
