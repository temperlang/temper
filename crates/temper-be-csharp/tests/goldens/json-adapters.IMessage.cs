using TemperLang.Std.Json;

namespace JsonAdapters;

internal interface IMessage
{
    void EncodeToJson(IJsonProducer p);

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static IMessage DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        {
            JsonObject obj;
            if (t is JsonObject)
            {
                obj = (JsonObject)t;
            }
            else
            {
                throw new JsonShapeException("expected Message JSON object");
            }
            IJsonSyntaxTree? valueForKind = obj.PropertyValueOrNull("kind");
            {
                if (valueForKind != null && valueForKind is JsonString)
                {
                    string subject = ((JsonString)valueForKind).Content;
                    if (subject == "hello")
                    {
                        return Hello.JsonAdapter().DecodeFromJson(obj, ic);
                    }
                    else if (subject == "bye")
                    {
                        return Bye.JsonAdapter().DecodeFromJson(obj, ic);
                    }
                }
            }
            throw new JsonShapeException("expected one of Message's JSON shapes");
        }
    }

    public static IJsonAdapter<IMessage> JsonAdapter()
    {
        return new MessageJsonAdapter();
    }
}
