package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class HelloJsonAdapter implements JsonAdapter<Hello> {
    public HelloJsonAdapter() {}

    public void encodeToJson(Hello x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Hello decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Hello.decodeFromJson(t, ic);
    }
}
