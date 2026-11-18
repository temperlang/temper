package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class ShapeJsonAdapter implements JsonAdapter<Shape> {
    public ShapeJsonAdapter() {}

    public void encodeToJson(Shape x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Shape decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Shape.decodeFromJson(t, ic);
    }
}
