package json_adapters;

import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonNumeric;
import temper.std.json.JsonProducer;
import temper.std.json.JsonSyntaxTree;

final class Celsius {
    public final int degrees;

    public Celsius(int degrees) {
        this.degrees = degrees;
    }

    public void encodeToJson(JsonProducer p) {
        p.int32Value(this.degrees);
    }

    public static Celsius decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        return new Celsius(((JsonNumeric) t).asInt32());
    }

    public static JsonAdapter<Celsius> jsonAdapter() {
        return new CelsiusJsonAdapter();
    }

    public int getDegrees() {
        return this.degrees;
    }
}
