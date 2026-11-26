package json_adapters;

import temper.core.Core;
import temper.core.ParseError;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonException;
import temper.std.json.JsonNumeric;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonShapeException;
import temper.std.json.JsonSyntaxTree;

final class Circle implements Shape {
    public final int radius;

    public Circle(int radius) {
        this.radius = radius;
    }

    public void encodeToJson(JsonProducer p) {
        p.startObject();
        p.objectKey("radius");
        p.int32Value(this.radius);
        p.endObject();
    }

    public static Circle decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) throws JsonException {
        JsonObject obj = t instanceof JsonObject ? (JsonObject) t : Core.throwFailure(new JsonShapeException("expected Circle JSON object"));
        JsonSyntaxTree radiusTree = obj.propertyValueOrBubble("radius");
        JsonNumeric radiusJson = radiusTree instanceof JsonNumeric ? (JsonNumeric) radiusTree : Core.throwFailure(new JsonShapeException("expected Int for radius"));
        int radius;
        try {
            radius = radiusJson.asInt32();
        } catch (ParseError failure) {
            throw Core.withCause(new JsonShapeException("expected Int for radius"), failure);
        }
        return new Circle(radius);
    }

    public static JsonAdapter<Circle> jsonAdapter() {
        return new CircleJsonAdapter();
    }

    public int getRadius() {
        return this.radius;
    }
}
