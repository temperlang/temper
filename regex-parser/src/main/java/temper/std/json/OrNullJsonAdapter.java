package temper.std.json;
import temper.core.Nullable;
public final class OrNullJsonAdapter<T__190> implements JsonAdapter<@Nullable T__190> {
    final JsonAdapter<T__190> adapterForT;
    public void encodeToJson(@Nullable T__190 x__868, JsonProducer p__869) {
        if (x__868 == null) {
            p__869.nullValue();
        } else {
            T__190 x_986 = x__868;
            this.adapterForT.encodeToJson(x_986, p__869);
        }
    }
    public @Nullable T__190 decodeFromJson(JsonSyntaxTree t__872, InterchangeContext ic__873) {
        if (t__872 instanceof JsonNull) {
            return null;
        } else {
            return this.adapterForT.decodeFromJson(t__872, ic__873);
        }
    }
    public OrNullJsonAdapter(JsonAdapter<T__190> adapterForT__876) {
        this.adapterForT = adapterForT__876;
    }
}
