package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class CelsiusJsonAdapter implements JsonAdapter<Celsius> {
    public CelsiusJsonAdapter() {}

    public void encodeToJson(Celsius x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Celsius decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Celsius.decodeFromJson(t, ic);
    }
}
