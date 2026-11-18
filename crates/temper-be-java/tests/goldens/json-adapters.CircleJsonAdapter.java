package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class CircleJsonAdapter implements JsonAdapter<Circle> {
    public CircleJsonAdapter() {}

    public void encodeToJson(Circle x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Circle decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Circle.decodeFromJson(t, ic);
    }
}
