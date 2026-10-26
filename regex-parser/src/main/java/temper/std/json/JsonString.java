package temper.std.json;
public final class JsonString implements JsonSyntaxTree {
    public final String content;
    public void produce(JsonProducer p__469) {
        p__469.stringValue(this.content);
    }
    public JsonString(String content__472) {
        this.content = content__472;
    }
    public String getContent() {
        return this.content;
    }
}
