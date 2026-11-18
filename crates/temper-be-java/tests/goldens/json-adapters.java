package json_adapters;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonGlobal;
import temper.std.json.JsonTextProducer;
import temper.std.json.NullInterchangeContext;

public final class JsonAdaptersGlobal {
    private JsonAdaptersGlobal() {}

    static final Console console;

    static <T> String encode(JsonAdapter<T> adapter, T value) {
        JsonTextProducer p = new JsonTextProducer();
        adapter.encodeToJson(value, p);
        try {
            return p.toJsonString();
        } catch (RuntimeException ignored) {
            return "bad JSON";
        }
    }

    static <T> T decode(JsonAdapter<T> adapter, String text) {
        return adapter.decodeFromJson(JsonGlobal.parseJson(text), NullInterchangeContext.instance);
    }

    static <T> void roundTrip(JsonAdapter<T> adapter, T value) {
        String text = encode(adapter, value);
        T back;
        try {
            back = decode(adapter, text);
        } catch (RuntimeException ignored) {
            throw new AssertionError("panic");
        }
        String again = encode(adapter, back);
        console.log(again.equals(text) ? text : text + " became " + again);
    }

    static {
        console = Core.getConsole(Logger.getLogger("json_adapters"));
        roundTrip(Point.jsonAdapter(), new Point(1, -2));
        roundTrip(Record.jsonAdapter(), new Record("Ada", true, 1.5, 123456789012L, null, List.of(new Point(3, 4))));
        List<Shape> shapes = List.of(new Circle(2), new Rect(3, 4));
        roundTrip(JsonGlobal.listJsonAdapter(Shape.jsonAdapter()), shapes);
        List<Message> messages = List.of(new Hello("hi"), new Bye("so long"));
        roundTrip(JsonGlobal.listJsonAdapter(Message.jsonAdapter()), messages);
        roundTrip(Celsius.jsonAdapter(), new Celsius(21));
        Shape shape;
        try {
            shape = decode(Shape.jsonAdapter(), "{\"height\": 6, \"width\": 5}");
        } catch (RuntimeException ignored) {
            throw new AssertionError("panic");
        }
        if (shape instanceof Rect) {
            console.log("rect " + ((Rect) shape).width + "x" + ((Rect) shape).height);
        } else {
            console.log("not a rect");
        }
        Record record_;
        try {
            record_ = decode(Record.jsonAdapter(), "{\"name\": \"Bo\", \"is-active\": false, \"score\": 2, \"big\": 7, \"nickname\": \"B\", \"points\": []}");
        } catch (RuntimeException ignored2) {
            throw new AssertionError("panic");
        }
        String t = record_.name + " " + record_.active + " ";
        @Nullable String subject = record_.nickname;
        console.log(t + (subject == null ? "none" : subject) + " " + record_.points.size());
        String t2;
        try {
            decode(Point.jsonAdapter(), "{\"x\": 1}");
            t2 = "decoded";
        } catch (RuntimeException ignored3) {
            t2 = "no y";
        }
        console.log(t2);
        String t3;
        try {
            decode(Message.jsonAdapter(), "{\"kind\": \"what\", \"text\": \"?\"}");
            t3 = "decoded";
        } catch (RuntimeException ignored4) {
            t3 = "no such kind";
        }
        console.log(t3);
    }
}
