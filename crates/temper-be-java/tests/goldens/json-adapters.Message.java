package json_adapters;

import temper.core.Core;
import temper.core.Nullable;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonException;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonShapeException;
import temper.std.json.JsonString;
import temper.std.json.JsonSyntaxTree;

interface Message {
    void encodeToJson(JsonProducer p);

    static Message decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) throws JsonException {
        {
            JsonObject obj = t instanceof JsonObject ? (JsonObject) t : Core.throwFailure(new JsonShapeException("expected Message JSON object"));
            @Nullable JsonSyntaxTree valueForKind = obj.propertyValueOrNull("kind");
            if (valueForKind != null && valueForKind instanceof JsonString) {
                String subject = ((JsonString) valueForKind).content;
                if (subject.equals("hello")) {
                    return Hello.jsonAdapter().decodeFromJson(obj, ic);
                } else if (subject.equals("bye")) {
                    return Bye.jsonAdapter().decodeFromJson(obj, ic);
                }
            }
            throw new JsonShapeException("expected one of Message's JSON shapes");
        }
    }

    static JsonAdapter<Message> jsonAdapter() {
        return new MessageJsonAdapter();
    }
}
