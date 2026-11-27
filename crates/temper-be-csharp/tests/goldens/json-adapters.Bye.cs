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

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static Bye DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj;
        if (t is JsonObject)
        {
            obj = (JsonObject)t;
        }
        else
        {
            throw new JsonShapeException("expected Bye JSON object");
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
        return new Bye(text);
    }

    public static IJsonAdapter<Bye> JsonAdapter()
    {
        return new ByeJsonAdapter();
    }
}
