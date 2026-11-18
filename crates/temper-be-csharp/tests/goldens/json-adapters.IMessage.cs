using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonAdapters;

internal interface IMessage
{
    void EncodeToJson(IJsonProducer p);

    public static IMessage DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        {
            JsonObject obj = (JsonObject)t;
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
            return Core.Bubble<IMessage>();
        }
    }

    public static IJsonAdapter<IMessage> JsonAdapter()
    {
        return new MessageJsonAdapter();
    }
}
