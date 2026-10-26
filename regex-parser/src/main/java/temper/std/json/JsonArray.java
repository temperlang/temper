package temper.std.json;
import java.util.List;
import temper.core.Core;
public final class JsonArray implements JsonSyntaxTree {
    public final List<JsonSyntaxTree> elements;
    public void produce(JsonProducer p__452) {
        p__452.startArray();
        List<JsonSyntaxTree> this__1149 = this.elements;
        int n__1151 = this__1149.size();
        int i__1152 = 0;
        while (i__1152 < n__1151) {
            JsonSyntaxTree el__1153 = Core.listGet(this__1149, i__1152);
            i__1152 = i__1152 + 1;
            JsonSyntaxTree v__454 = el__1153;
            v__454.produce(p__452);
        }
        p__452.endArray();
    }
    public JsonArray(List<JsonSyntaxTree> elements__456) {
        this.elements = elements__456;
    }
    public List<JsonSyntaxTree> getElements() {
        return this.elements;
    }
}
