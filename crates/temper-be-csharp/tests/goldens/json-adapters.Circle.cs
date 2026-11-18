using TemperLang.Std.Json;

namespace JsonAdapters;

internal class Circle : IShape
{
    private readonly int radius;

    public Circle(int radius)
    {
        this.radius = radius;
    }

    public int Radius
    {
        get
        {
            return this.radius;
        }
    }

    public void EncodeToJson(IJsonProducer p)
    {
        p.StartObject();
        p.ObjectKey("radius");
        p.Int32_Value(this.radius);
        p.EndObject();
    }

    public static Circle DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj = (JsonObject)t;
        IJsonNumeric radiusJson = (IJsonNumeric)obj.PropertyValueOrBubble("radius");
        int radius = radiusJson.AsInt32();
        return new Circle(radius);
    }

    public static IJsonAdapter<Circle> JsonAdapter()
    {
        return new CircleJsonAdapter();
    }
}
