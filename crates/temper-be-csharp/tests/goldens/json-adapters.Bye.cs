using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonAdapters;

internal class Bye : IMessage
{
    private readonly string text;

    public Bye(string text)
    {
        this.text = text;
    }

    public string Text
    {
        get
        {
            return this.text;
        }
    }

    public void EncodeToJson(IJsonProducer p)
    {
        p.StartObject();
        p.ObjectKey("text");
        p.StringValue(this.text);
        p.ObjectKey("kind");
        p.StringValue("bye");
        p.EndObject();
    }

    public static Bye DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj = t is JsonObject ? (JsonObject)t : Core.Bubble<JsonObject>();
        IJsonSyntaxTree textTree = obj.PropertyValueOrBubble("text");
        JsonString textJson = textTree is JsonString ? (JsonString)textTree : Core.Bubble<JsonString>();
        string text = textJson.Content;
        return new Bye(text);
    }

    public static IJsonAdapter<Bye> JsonAdapter()
    {
        return new ByeJsonAdapter();
    }
}
