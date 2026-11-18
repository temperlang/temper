package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class ByeJsonAdapter implements JsonAdapter<Bye> {
    public ByeJsonAdapter() {}

    public void encodeToJson(Bye x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Bye decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Bye.decodeFromJson(t, ic);
    }
}
