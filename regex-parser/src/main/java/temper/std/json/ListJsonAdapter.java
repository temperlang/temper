package temper.std.json;
import java.util.List;
import temper.core.Core;
import java.util.ArrayList;
final class ListJsonAdapter<T__186> implements JsonAdapter<List<T__186>> {
    final JsonAdapter<T__186> adapterForT;
    public void encodeToJson(List<T__186> x__849, JsonProducer p__850) {
        p__850.startArray();
        List<T__186> this__1154 = x__849;
        int n__1156 = this__1154.size();
        int i__1157 = 0;
        while (i__1157 < n__1156) {
            T__186 el__1158 = Core.listGet(this__1154, i__1157);
            i__1157 = i__1157 + 1;
            T__186 el__852 = el__1158;
            this.adapterForT.encodeToJson(el__852, p__850);
        }
        p__850.endArray();
    }
    public List<T__186> decodeFromJson(JsonSyntaxTree t__854, InterchangeContext ic__855) {
        List<T__186> b__857 = new ArrayList<>();
        JsonArray t_996 = Core.cast(JsonArray.class, t__854);
        List<JsonSyntaxTree> elements__858 = t_996.getElements();
        int n__859 = elements__858.size();
        int i__860 = 0;
        while (i__860 < n__859) {
            JsonSyntaxTree el__861 = Core.listGet(elements__858, i__860);
            i__860 = i__860 + 1;
            T__186 t_1159 = this.adapterForT.decodeFromJson(el__861, ic__855);
            Core.listAdd(b__857, t_1159);
        }
        return List.copyOf(b__857);
    }
    public ListJsonAdapter(JsonAdapter<T__186> adapterForT__863) {
        this.adapterForT = adapterForT__863;
    }
}
