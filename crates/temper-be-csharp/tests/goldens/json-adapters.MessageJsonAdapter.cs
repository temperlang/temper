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

    public IMessage DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return IMessage.DecodeFromJson(t, ic);
    }
}
