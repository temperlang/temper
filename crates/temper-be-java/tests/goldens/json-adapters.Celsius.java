package json_adapters;

import temper.core.Core;
import temper.core.ParseError;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonException;
import temper.std.json.JsonNumeric;
import temper.std.json.JsonProducer;
import temper.std.json.JsonShapeException;
import temper.std.json.JsonSyntaxTree;

final class Celsius {
    public final int degrees;

    public Celsius(int degrees) {
        this.degrees = degrees;
    }

    public void encodeToJson(JsonProducer p) {
        p.int32Value(this.degrees);
    }

    public static Celsius decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) throws JsonException {
        int t2;
        try {
            t2 = Core.cast(JsonNumeric.class, t).asInt32();
        } catch (ParseError failure) {
            throw Core.withCause(new JsonShapeException("expected degrees"), failure);
        }
        return new Celsius(t2);
    }

    public static JsonAdapter<Celsius> jsonAdapter() {
        return new CelsiusJsonAdapter();
    }

    public int getDegrees() {
        return this.degrees;
    }
}
