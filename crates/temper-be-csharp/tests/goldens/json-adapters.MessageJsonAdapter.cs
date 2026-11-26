using TemperLang.Std.Json;

namespace JsonAdapters;

internal class MessageJsonAdapter : IJsonAdapter<IMessage>
{
    public MessageJsonAdapter()
    {
    }

    public void EncodeToJson(IMessage x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public IMessage DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return IMessage.DecodeFromJson(t, ic);
    }
}
