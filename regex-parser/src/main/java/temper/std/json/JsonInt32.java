package temper.std.json;
public final class JsonInt32 implements JsonNumeric {
    public final int content;
    public void produce(JsonProducer p__483) {
        p__483.int32Value(this.content);
    }
    public String asJsonNumericToken() {
        return Integer.toString(this.content);
    }
    public int asInt32() {
        return this.content;
    }
    public int asInt32Safe() {
        return this.content;
    }
    public long asInt64() {
        return this.asInt64Safe();
    }
    public long asInt64Safe() {
        return (long) this.content;
    }
    public double asFloat64() {
        return this.asFloat64Safe();
    }
    public double asFloat64Safe() {
        return (double) this.content;
    }
    public JsonInt32(int content__500) {
        this.content = content__500;
    }
    public int getContent() {
        return this.content;
    }
}
