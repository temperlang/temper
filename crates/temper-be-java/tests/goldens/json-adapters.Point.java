package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonNumeric;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class Point {
    public final int x;
    public final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void encodeToJson(JsonProducer p) {
        p.startObject();
        p.objectKey("x");
        p.int32Value(this.x);
        p.objectKey("y");
        p.int32Value(this.y);
        p.endObject();
    }

    public static Point decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        JsonObject obj = (JsonObject) t;
        JsonNumeric xJson = (JsonNumeric) obj.propertyValueOrBubble("x");
        int x = xJson.asInt32();
        JsonNumeric yJson = (JsonNumeric) obj.propertyValueOrBubble("y");
        int y = yJson.asInt32();
        return new Point(x, y);
    }

    public static JsonAdapter<Point> jsonAdapter() {
        return new PointJsonAdapter();
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public static final class Builder {
        int x;
        boolean x__set;

        public Builder x(int x) {
            x__set = true;
            this.x = x;
            return this;
        }

        int y;
        boolean y__set;

        public Builder y(int y) {
            y__set = true;
            this.y = y;
            return this;
        }

        public Point build() {
            if (!x__set || !y__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!x__set) {
                    _message.append(" x");
                }
                if (!y__set) {
                    _message.append(" y");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Point(x, y);
        }
    }
}
