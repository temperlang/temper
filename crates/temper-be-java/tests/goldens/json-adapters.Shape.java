package json_adapters;

import temper.core.Core;
import temper.core.Nullable;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonException;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonShapeException;
import temper.std.json.JsonSyntaxTree;

interface Shape {
    void encodeToJson(JsonProducer p);

    static Shape decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) throws JsonException {
        {
            JsonObject obj = t instanceof JsonObject ? (JsonObject) t : Core.throwFailure(new JsonShapeException("expected Shape JSON object"));
            @Nullable JsonSyntaxTree valueForRadius = obj.propertyValueOrNull("radius");
            if (valueForRadius != null) {
                return Circle.jsonAdapter().decodeFromJson(obj, ic);
            }
            if (valueForRadius == null) {
                return Rect.jsonAdapter().decodeFromJson(obj, ic);
            }
            throw new JsonShapeException("expected one of Shape's JSON shapes");
        }
    }

    static JsonAdapter<Shape> jsonAdapter() {
        return new ShapeJsonAdapter();
    }
}
