using TemperLang.Std.Json;

namespace JsonAdapters;

internal class Hello : IMessage
{
    private readonly string text;

    public Hello(string text)
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
        p.StringValue("hello");
        p.EndObject();
    }

    public static Hello DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj = (JsonObject)t;
        JsonString textJson = (JsonString)obj.PropertyValueOrBubble("text");
        string text = textJson.Content;
        return new Hello(text);
    }

    public static IJsonAdapter<Hello> JsonAdapter()
    {
        return new HelloJsonAdapter();
    }
}
