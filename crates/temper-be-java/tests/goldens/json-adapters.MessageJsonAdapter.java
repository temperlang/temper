package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class MessageJsonAdapter implements JsonAdapter<Message> {
    public MessageJsonAdapter() {}

    public void encodeToJson(Message x, JsonProducer p) {
        x.encodeToJson(p);
    }

    public Message decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return Message.decodeFromJson(t, ic);
    }
}
