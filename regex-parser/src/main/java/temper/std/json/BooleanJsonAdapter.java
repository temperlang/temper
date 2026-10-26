package temper.std.json;
import temper.core.Core;
final class BooleanJsonAdapter implements JsonAdapter<Boolean> {
    private void encodeToJson__797(boolean x__798, JsonProducer p__799) {
        p__799.booleanValue(x__798);
    }
    public void encodeToJson(Boolean x__798, JsonProducer p__799) {
        encodeToJson__797(x__798.booleanValue(), p__799);
    }
    private boolean decodeFromJson__801(JsonSyntaxTree t__802, InterchangeContext ic__803) {
        JsonBoolean t_1001 = Core.cast(JsonBoolean.class, t__802);
        return t_1001.isContent();
    }
    public Boolean decodeFromJson(JsonSyntaxTree t__802, InterchangeContext ic__803) {
        return decodeFromJson__801(t__802, ic__803);
    }
    public BooleanJsonAdapter() {
    }
}
