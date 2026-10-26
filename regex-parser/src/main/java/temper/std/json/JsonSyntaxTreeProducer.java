package temper.std.json;
import temper.core.Core;
import java.util.List;
import temper.core.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import static temper.std.json.NullInterchangeContext.instance;
public final class JsonSyntaxTreeProducer implements JsonProducer, JsonParseErrorReceiver {
    final List<List<JsonSyntaxTree>> stack;
    @Nullable String error;
    public InterchangeContext getInterchangeContext() {
        return instance;
    }
    public JsonSyntaxTreeProducer() {
        List<List<JsonSyntaxTree>> t_882 = new ArrayList<>();
        this.stack = t_882;
        Core.listAdd(this.stack, new ArrayList<>());
        this.error = null;
    }
    void storeValue(JsonSyntaxTree v__620) {
        if (!this.stack.isEmpty()) {
            Core.listAdd(Core.listGet(this.stack, this.stack.size() - 1), v__620);
        }
    }
    public void startObject() {
        Core.listAdd(this.stack, new ArrayList<>());
    }
    public void endObject() {
        fn__625: {
            if (this.stack.isEmpty()) {
                break fn__625;
            }
            List<JsonSyntaxTree> ls__626 = Core.listRemoveLast(this.stack);
            Map<String, List<JsonSyntaxTree>> m__627 = new LinkedHashMap<>();
            @Nullable Map<String, List<JsonSyntaxTree>> multis__628 = null;
            int i__629 = 0;
            int n__630 = ls__626.size() & -2;
            while (i__629 < n__630) {
                int postfixReturn_34 = i__629;
                i__629 = postfixReturn_34 + 1;
                JsonSyntaxTree keyTree__631 = Core.listGet(ls__626, postfixReturn_34);
                if (!(keyTree__631 instanceof JsonString)) {
                    break;
                }
                JsonString t_1122 = Core.cast(JsonString.class, keyTree__631);
                String key__632 = t_1122.getContent();
                int postfixReturn_35 = i__629;
                i__629 = postfixReturn_35 + 1;
                JsonSyntaxTree value__633 = Core.listGet(ls__626, postfixReturn_35);
                if (m__627.containsKey(key__632)) {
                    if (multis__628 == null) {
                        multis__628 = new LinkedHashMap<>();
                    }
                    Map<String, List<JsonSyntaxTree>> mb__634;
                    if (multis__628 == null) {
                        throw Core.bubble();
                    } else {
                        mb__634 = multis__628;
                    }
                    if (!mb__634.containsKey(key__632)) {
                        List<JsonSyntaxTree> t_1124 = Core.mappedGet(m__627, key__632);
                        mb__634.put(key__632, new ArrayList<>(t_1124));
                    }
                    List<JsonSyntaxTree> t_1125 = Core.mappedGet(mb__634, key__632);
                    Core.listAdd(t_1125, value__633);
                } else {
                    m__627.put(key__632, List.of(value__633));
                }
            }
            @Nullable Map<String, List<JsonSyntaxTree>> multis__635 = multis__628;
            if (multis__635 != null) {
                BiConsumer<String, List<JsonSyntaxTree>> fn__1162 = (k__636, vs__637) -> {
                    m__627.put(k__636, List.copyOf(vs__637));
                };
                Core.mappedForEach(multis__635, fn__1162);
            }
            this.storeValue(new JsonObject(Core.mappedToMap(m__627)));
        }
    }
    public void objectKey(String key__639) {
        this.storeValue(new JsonString(key__639));
    }
    public void startArray() {
        Core.listAdd(this.stack, new ArrayList<>());
    }
    public void endArray() {
        fn__644: {
            if (this.stack.isEmpty()) {
                break fn__644;
            }
            List<JsonSyntaxTree> ls__645 = Core.listRemoveLast(this.stack);
            this.storeValue(new JsonArray(List.copyOf(ls__645)));
        }
    }
    public void nullValue() {
        this.storeValue(new JsonNull());
    }
    public void booleanValue(boolean x__649) {
        this.storeValue(new JsonBoolean(x__649));
    }
    public void int32Value(int x__652) {
        this.storeValue(new JsonInt32(x__652));
    }
    public void int64Value(long x__655) {
        this.storeValue(new JsonInt64(x__655));
    }
    public void float64Value(double x__658) {
        this.storeValue(new JsonFloat64(x__658));
    }
    public void numericTokenValue(String x__661) {
        this.storeValue(new JsonNumericToken(x__661));
    }
    public void stringValue(String x__664) {
        this.storeValue(new JsonString(x__664));
    }
    public JsonSyntaxTree toJsonSyntaxTree() {
        boolean t_1118;
        if (this.stack.size() != 1) {
            t_1118 = true;
        } else {
            t_1118 = this.error != null;
        }
        if (t_1118) {
            throw Core.bubble();
        }
        List<JsonSyntaxTree> ls__668 = Core.listGet(this.stack, 0);
        if (ls__668.size() != 1) {
            throw Core.bubble();
        }
        return Core.listGet(ls__668, 0);
    }
    public @Nullable String getJsonError() {
        return this.error;
    }
    public @Nullable JsonParseErrorReceiver getParseErrorReceiver() {
        return this;
    }
    public JsonParseErrorReceiver getParseErrorReceiverSafe() {
        return this;
    }
    public void explainJsonError(String error__676) {
        this.error = error__676;
    }
}
