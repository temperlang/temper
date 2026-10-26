package temper.std.json;
import temper.core.Core;
public final class JsonNumericToken implements JsonNumeric {
    public final String content;
    public void produce(JsonProducer p__535) {
        p__535.numericTokenValue(this.content);
    }
    public String asJsonNumericToken() {
        return this.content;
    }
    public int asInt32() {
        try {
            return Core.stringToInt(this.content);
        } catch (RuntimeException ignored$1) {
            double t_1161 = Core.stringToFloat64(this.content);
            return Core.float64ToInt(t_1161);
        }
    }
    public long asInt64() {
        try {
            return Core.stringToInt64(this.content);
        } catch (RuntimeException ignored$2) {
            double t_1160 = Core.stringToFloat64(this.content);
            return Core.float64ToInt64(t_1160);
        }
    }
    public double asFloat64() {
        return Core.stringToFloat64(this.content);
    }
    public JsonNumericToken(String content__546) {
        this.content = content__546;
    }
    public String getContent() {
        return this.content;
    }
}
