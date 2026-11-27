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

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static Hello DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj;
        if (t is JsonObject)
        {
            obj = (JsonObject)t;
        }
        else
        {
            throw new JsonShapeException("expected Hello JSON object");
        }
        IJsonSyntaxTree textTree = obj.PropertyValueOrThrow("text");
        JsonString textJson;
        if (textTree is JsonString)
        {
            textJson = (JsonString)textTree;
        }
        else
        {
            throw new JsonShapeException("expected String for text");
        }
        string text = textJson.Content;
        return new Hello(text);
    }

    public static IJsonAdapter<Hello> JsonAdapter()
    {
        return new HelloJsonAdapter();
    }
}
