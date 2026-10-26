package temper.std.temporal;
import java.time.LocalDate;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;
import temper.std.json.InterchangeContext;
final class DateJsonAdapter implements JsonAdapter<LocalDate> {
    public void encodeToJson(LocalDate x__117, JsonProducer p__118) {
        TemporalGlobal.encodeToJson(x__117, p__118);
    }
    public LocalDate decodeFromJson(JsonSyntaxTree t__119, InterchangeContext ic__120) {
        return TemporalGlobal.decodeFromJson(t__119, ic__120);
    }
    public DateJsonAdapter() {
    }
}
