package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class RectJsonAdapter implements JsonAdapter<Rect> {
    public RectJsonAdapter() {}

    public void encodeToJson(Rect x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Rect decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Rect.decodeFromJson(t, ic);
    }
}
