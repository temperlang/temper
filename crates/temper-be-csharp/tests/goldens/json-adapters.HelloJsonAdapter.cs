using TemperLang.Std.Json;

namespace JsonAdapters;

internal class HelloJsonAdapter : IJsonAdapter<Hello>
{
    public HelloJsonAdapter()
    {
    }

    public void EncodeToJson(Hello x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    public Hello DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Hello.DecodeFromJson(t, ic);
    }
}
