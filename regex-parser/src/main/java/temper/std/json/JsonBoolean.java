package temper.std.json;
public final class JsonBoolean implements JsonSyntaxTree {
    public final boolean content;
    public void produce(JsonProducer p__459) {
        p__459.booleanValue(this.content);
    }
    public JsonBoolean(boolean content__462) {
        this.content = content__462;
    }
    public boolean isContent() {
        return this.content;
    }
}
