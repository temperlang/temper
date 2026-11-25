using TemperLang.Core;
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
        JsonObject obj = t is JsonObject ? (JsonObject)t : Core.Bubble<JsonObject>();
        IJsonSyntaxTree radiusTree = obj.PropertyValueOrBubble("radius");
        IJsonNumeric radiusJson = radiusTree is IJsonNumeric ? (IJsonNumeric)radiusTree : Core.Bubble<IJsonNumeric>();
        int radius = radiusJson.AsInt32();
        return new Circle(radius);
    }

    public static IJsonAdapter<Circle> JsonAdapter()
    {
        return new CircleJsonAdapter();
    }
}
