package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class RecordJsonAdapter implements JsonAdapter<Record> {
    public RecordJsonAdapter() {}

    public void encodeToJson(Record x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Record decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Record.decodeFromJson(t, ic);
    }
}
