package temper.std.json;
import temper.core.Core;
final class Int32JsonAdapter implements JsonAdapter<Integer> {
    private void encodeToJson__817(int x__818, JsonProducer p__819) {
        p__819.int32Value(x__818);
    }
    public void encodeToJson(Integer x__818, JsonProducer p__819) {
        encodeToJson__817(x__818.intValue(), p__819);
    }
    private int decodeFromJson__821(JsonSyntaxTree t__822, InterchangeContext ic__823) {
        JsonNumeric t_999 = Core.cast(JsonNumeric.class, t__822);
        return t_999.asInt32();
    }
    public Integer decodeFromJson(JsonSyntaxTree t__822, InterchangeContext ic__823) {
        return decodeFromJson__821(t__822, ic__823);
    }
    public Int32JsonAdapter() {
    }
}
