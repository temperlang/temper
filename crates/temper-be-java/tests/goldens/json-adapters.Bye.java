package json_adapters;

import temper.core.Core;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonException;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonShapeException;
import temper.std.json.JsonString;
import temper.std.json.JsonSyntaxTree;

final class Bye implements Message {
    public final String text;

    public Bye(String text) {
        this.text = text;
    }

    public void encodeToJson(JsonProducer p) {
        p.startObject();
        p.objectKey("text");
        p.stringValue(this.text);
        p.objectKey("kind");
        p.stringValue("bye");
        p.endObject();
    }

    public static Bye decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) throws JsonException {
        JsonObject obj = t instanceof JsonObject ? (JsonObject) t : Core.throwFailure(new JsonShapeException("expected Bye JSON object"));
        JsonSyntaxTree textTree = obj.propertyValueOrThrow("text");
        JsonString textJson = textTree instanceof JsonString ? (JsonString) textTree : Core.throwFailure(new JsonShapeException("expected String for text"));
        String text = textJson.content;
        return new Bye(text);
    }

    public static JsonAdapter<Bye> jsonAdapter() {
        return new ByeJsonAdapter();
    }

    public String getText() {
        return this.text;
    }
}
