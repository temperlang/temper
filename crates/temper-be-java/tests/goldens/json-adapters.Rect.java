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

final class Rect implements Shape {
    public final int width;
    public final int height;

    public Rect(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void encodeToJson(JsonProducer p) {
        p.startObject();
        p.objectKey("width");
        p.int32Value(this.width);
        p.objectKey("height");
        p.int32Value(this.height);
        p.endObject();
    }

    public static Rect decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) throws JsonException {
        JsonObject obj = t instanceof JsonObject ? (JsonObject) t : Core.throwFailure(new JsonShapeException("expected Rect JSON object"));
        JsonSyntaxTree widthTree = obj.propertyValueOrBubble("width");
        JsonNumeric widthJson = widthTree instanceof JsonNumeric ? (JsonNumeric) widthTree : Core.throwFailure(new JsonShapeException("expected Int for width"));
        int width;
        try {
            width = widthJson.asInt32();
        } catch (ParseError failure) {
            throw Core.withCause(new JsonShapeException("expected Int for width"), failure);
        }
        JsonSyntaxTree heightTree = obj.propertyValueOrBubble("height");
        JsonNumeric heightJson = heightTree instanceof JsonNumeric ? (JsonNumeric) heightTree : Core.throwFailure(new JsonShapeException("expected Int for height"));
        int height;
        try {
            height = heightJson.asInt32();
        } catch (ParseError failure2) {
            throw Core.withCause(new JsonShapeException("expected Int for height"), failure2);
        }
        return new Rect(width, height);
    }

    public static JsonAdapter<Rect> jsonAdapter() {
        return new RectJsonAdapter();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public static final class Builder {
        int width;
        boolean width__set;

        public Builder width(int width) {
            width__set = true;
            this.width = width;
            return this;
        }

        int height;
        boolean height__set;

        public Builder height(int height) {
            height__set = true;
            this.height = height;
            return this;
        }

        public Rect build() {
            if (!width__set || !height__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!width__set) {
                    _message.append(" width");
                }
                if (!height__set) {
                    _message.append(" height");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Rect(width, height);
        }
    }
}
