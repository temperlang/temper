package temper.std.json;
import temper.core.Core;
final class Float64JsonAdapter implements JsonAdapter<Double> {
    private void encodeToJson__807(double x__808, JsonProducer p__809) {
        p__809.float64Value(x__808);
    }
    public void encodeToJson(Double x__808, JsonProducer p__809) {
        encodeToJson__807(x__808.doubleValue(), p__809);
    }
    private double decodeFromJson__811(JsonSyntaxTree t__812, InterchangeContext ic__813) {
        JsonNumeric t_1000 = Core.cast(JsonNumeric.class, t__812);
        return t_1000.asFloat64();
    }
    public Double decodeFromJson(JsonSyntaxTree t__812, InterchangeContext ic__813) {
        return decodeFromJson__811(t__812, ic__813);
    }
    public Float64JsonAdapter() {
    }
}
