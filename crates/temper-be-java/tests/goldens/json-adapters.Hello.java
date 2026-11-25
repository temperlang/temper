package json_adapters;

import temper.core.Core;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonString;
import temper.std.json.JsonSyntaxTree;

final class Hello implements Message {
    public final String text;

    public Hello(String text) {
        this.text = text;
    }

    public void encodeToJson(JsonProducer p) {
        p.startObject();
        p.objectKey("text");
        p.stringValue(this.text);
        p.objectKey("kind");
        p.stringValue("hello");
        p.endObject();
    }

    public static Hello decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        JsonObject obj = t instanceof JsonObject ? (JsonObject) t : Core.throwBubble();
        JsonSyntaxTree textTree = obj.propertyValueOrBubble("text");
        JsonString textJson = textTree instanceof JsonString ? (JsonString) textTree : Core.throwBubble();
        String text = textJson.content;
        return new Hello(text);
    }

    public static JsonAdapter<Hello> jsonAdapter() {
        return new HelloJsonAdapter();
    }

    public String getText() {
        return this.text;
    }
}
