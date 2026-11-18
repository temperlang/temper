package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class PointJsonAdapter implements JsonAdapter<Point> {
    public PointJsonAdapter() {}

    public void encodeToJson(Point x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Point decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Point.decodeFromJson(t, ic);
    }
}
