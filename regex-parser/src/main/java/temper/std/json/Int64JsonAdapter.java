package temper.std.json;
import temper.core.Core;
final class Int64JsonAdapter implements JsonAdapter<Long> {
    private void encodeToJson__827(long x__828, JsonProducer p__829) {
        p__829.int64Value(x__828);
    }
    public void encodeToJson(Long x__828, JsonProducer p__829) {
        encodeToJson__827(x__828.longValue(), p__829);
    }
    private long decodeFromJson__831(JsonSyntaxTree t__832, InterchangeContext ic__833) {
        JsonNumeric t_998 = Core.cast(JsonNumeric.class, t__832);
        return t_998.asInt64();
    }
    public Long decodeFromJson(JsonSyntaxTree t__832, InterchangeContext ic__833) {
        return decodeFromJson__831(t__832, ic__833);
    }
    public Int64JsonAdapter() {
    }
}
