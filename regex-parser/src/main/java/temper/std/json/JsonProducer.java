package temper.std.json;
import temper.core.Nullable;
public interface JsonProducer {
    InterchangeContext getInterchangeContext();
    void startObject();
    void endObject();
    void objectKey(String key__402);
    void startArray();
    void endArray();
    void nullValue();
    void booleanValue(boolean x__411);
    void int32Value(int x__414);
    void int64Value(long x__417);
    void float64Value(double x__420);
    /**
     * A number that fits the JSON number grammar to allow
     * interchange of numbers that are not easily representible
     * using numeric types that Temper connects to.
     */
    void numericTokenValue(String x__423);
    void stringValue(String x__426);
    default @Nullable JsonParseErrorReceiver getParseErrorReceiver() {
        return null;
    }
}
