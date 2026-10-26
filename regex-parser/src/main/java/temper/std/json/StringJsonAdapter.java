package temper.std.json;
import temper.core.Core;
final class StringJsonAdapter implements JsonAdapter<String> {
    public void encodeToJson(String x__838, JsonProducer p__839) {
        p__839.stringValue(x__838);
    }
    public String decodeFromJson(JsonSyntaxTree t__842, InterchangeContext ic__843) {
        JsonString t_997 = Core.cast(JsonString.class, t__842);
        return t_997.getContent();
    }
    public StringJsonAdapter() {
    }
}
