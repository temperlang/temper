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

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static Circle DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj;
        if (t is JsonObject)
        {
            obj = (JsonObject)t;
        }
        else
        {
            throw new JsonShapeException("expected Circle JSON object");
        }
        IJsonSyntaxTree radiusTree = obj.PropertyValueOrThrow("radius");
        IJsonNumeric radiusJson;
        if (radiusTree is IJsonNumeric)
        {
            radiusJson = (IJsonNumeric)radiusTree;
        }
        else
        {
            throw new JsonShapeException("expected Int for radius");
        }
        int radius;
        try
        {
            radius = radiusJson.AsInt32();
        }
        catch (ParseError failure)
        {
            throw Core.WithCause(new JsonShapeException("expected Int for radius"), failure);
        }
        return new Circle(radius);
    }

    public static IJsonAdapter<Circle> JsonAdapter()
    {
        return new CircleJsonAdapter();
    }
}
