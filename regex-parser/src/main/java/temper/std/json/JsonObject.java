package temper.std.json;
import java.util.List;
import temper.core.Core;
import java.util.Map;
import temper.core.Nullable;
import java.util.function.BiConsumer;
public final class JsonObject implements JsonSyntaxTree {
    public final Map<String, List<JsonSyntaxTree>> properties;
    /**
     * The JSON value tree associated with the given property key or null
     * if there is no such value.
     *
     * The properties map contains a list of sub-trees because JSON
     * allows duplicate properties.  ECMA-404 §6 notes (emphasis added):
     *
     * &gt; The JSON syntax does not impose any restrictions on the strings
     * &gt; used as names, &#42;&#42;does not require that name strings be unique&#42;&#42;,
     * &gt; and does not assign any significance to the ordering of
     * &gt; name/value pairs.
     *
     * When widely used JSON parsers need to relate a property key
     * to a single value, they tend to prefer the last key/value pair
     * from a JSON object.  For example:
     *
     * JS:
     *
     *     JSON.parse('&#123;"x":"first","x":"last"&#125;').x === 'last'
     *
     * Python:
     *
     *     import json
     *     json.loads('&#123;"x":"first","x":"last"&#125;')['x'] == 'last'
     *
     * C#:
     *
     *    using System.Text.Json;
     * 		JsonDocument d = JsonDocument.Parse(
     * 			"""
     * 			&#123;"x":"first","x":"last"&#125;
     * 			"""
     * 		);
     * 		d.RootElement.GetProperty("x").GetString() == "last"
     */
    public @Nullable JsonSyntaxTree propertyValueOrNull(String propertyKey__435) {
        List<JsonSyntaxTree> treeList__437 = this.properties.getOrDefault(propertyKey__435, List.of());
        int lastIndex__438 = treeList__437.size() - 1;
        if (lastIndex__438 >= 0) {
            return Core.listGet(treeList__437, lastIndex__438);
        } else {
            return null;
        }
    }
    public JsonSyntaxTree propertyValueOrBubble(String propertyKey__440) {
        @Nullable JsonSyntaxTree t_1142 = this.propertyValueOrNull(propertyKey__440);
        if (t_1142 == null) {
            throw Core.bubble();
        } else {
            return t_1142;
        }
    }
    public void produce(JsonProducer p__443) {
        p__443.startObject();
        BiConsumer<String, List<JsonSyntaxTree>> fn__1163 = (k__445, vs__446) -> {
            List<JsonSyntaxTree> this__1144 = vs__446;
            int n__1146 = this__1144.size();
            int i__1147 = 0;
            while (i__1147 < n__1146) {
                JsonSyntaxTree el__1148 = Core.listGet(this__1144, i__1147);
                i__1147 = i__1147 + 1;
                JsonSyntaxTree v__447 = el__1148;
                p__443.objectKey(k__445);
                v__447.produce(p__443);
            }
        };
        Core.mappedForEach(this.properties, fn__1163);
        p__443.endObject();
    }
    public JsonObject(Map<String, List<JsonSyntaxTree>> properties__449) {
        this.properties = properties__449;
    }
    public Map<String, List<JsonSyntaxTree>> getProperties() {
        return this.properties;
    }
}
