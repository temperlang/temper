using TemperLang.Std.Json;

namespace JsonAdapters;

internal class RecordJsonAdapter : IJsonAdapter<Record>
{
    public RecordJsonAdapter()
    {
    }

    public void EncodeToJson(Record x, IJsonProducer p)
    {
        x.EncodeToJson(p);
    }

    public Record DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        return Record.DecodeFromJson(t, ic);
    }
}
