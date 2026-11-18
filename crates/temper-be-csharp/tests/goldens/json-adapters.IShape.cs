using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonAdapters;

internal interface IShape
{
    void EncodeToJson(IJsonProducer p);

    public static IShape DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        {
            JsonObject obj = (JsonObject)t;
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
            return Core.Bubble<IShape>();
        }
    }

    public static IJsonAdapter<IShape> JsonAdapter()
    {
        return new ShapeJsonAdapter();
    }
}
