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

    public static Point decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) throws JsonException {
        JsonObject obj = t instanceof JsonObject ? (JsonObject) t : Core.throwFailure(new JsonShapeException("expected Point JSON object"));
        JsonSyntaxTree xTree = obj.propertyValueOrThrow("x");
        JsonNumeric xJson = xTree instanceof JsonNumeric ? (JsonNumeric) xTree : Core.throwFailure(new JsonShapeException("expected Int for x"));
        int x;
        try {
            x = xJson.asInt32();
        } catch (ParseError failure) {
            throw Core.withCause(new JsonShapeException("expected Int for x"), failure);
        }
        JsonSyntaxTree yTree = obj.propertyValueOrThrow("y");
        JsonNumeric yJson = yTree instanceof JsonNumeric ? (JsonNumeric) yTree : Core.throwFailure(new JsonShapeException("expected Int for y"));
        int y;
        try {
            y = yJson.asInt32();
        } catch (ParseError failure2) {
            throw Core.withCause(new JsonShapeException("expected Int for y"), failure2);
        }
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
