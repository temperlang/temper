package json_adapters;

import temper.core.Core;
import temper.core.Nullable;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

interface Shape {
    void encodeToJson(JsonProducer p);

    static Shape decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        {
            JsonObject obj = (JsonObject) t;
            @Nullable JsonSyntaxTree valueForRadius = obj.propertyValueOrNull("radius");
            if (valueForRadius != null) {
                return Circle.jsonAdapter().decodeFromJson(obj, ic);
            }
            if (valueForRadius == null) {
                return Rect.jsonAdapter().decodeFromJson(obj, ic);
            }
            throw Core.bubble();
        }
    }

    static JsonAdapter<Shape> jsonAdapter() {
        return new ShapeJsonAdapter();
    }
}
