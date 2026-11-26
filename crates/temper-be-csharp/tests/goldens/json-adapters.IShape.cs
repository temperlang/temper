using TemperLang.Std.Json;

namespace JsonAdapters;

internal interface IShape
{
    void EncodeToJson(IJsonProducer p);

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static IShape DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        {
            JsonObject obj;
            if (t is JsonObject)
            {
                obj = (JsonObject)t;
            }
            else
            {
                throw new JsonShapeException("expected Shape JSON object");
            }
            IJsonSyntaxTree? valueForRadius = obj.PropertyValueOrNull("radius");
            {
                if (valueForRadius != null)
                {
                    return Circle.JsonAdapter().DecodeFromJson(obj, ic);
                }
                if (valueForRadius == null)
                {
                    return Rect.JsonAdapter().DecodeFromJson(obj, ic);
                }
            }
            throw new JsonShapeException("expected one of Shape's JSON shapes");
        }
    }

    public static IJsonAdapter<IShape> JsonAdapter()
    {
        return new ShapeJsonAdapter();
    }
}
