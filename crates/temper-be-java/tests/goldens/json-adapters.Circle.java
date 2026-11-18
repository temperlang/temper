package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonNumeric;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
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

    public static Circle decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        JsonObject obj = (JsonObject) t;
        JsonNumeric radiusJson = (JsonNumeric) obj.propertyValueOrBubble("radius");
        int radius = radiusJson.asInt32();
        return new Circle(radius);
    }

    public static JsonAdapter<Circle> jsonAdapter() {
        return new CircleJsonAdapter();
    }

    public int getRadius() {
        return this.radius;
    }
}
