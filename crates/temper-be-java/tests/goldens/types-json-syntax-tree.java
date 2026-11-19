package json_syntax_tree;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;
import temper.std.json.JsonArray;
import temper.std.json.JsonGlobal;
import temper.std.json.JsonNull;
import temper.std.json.JsonNumeric;
import temper.std.json.JsonObject;
import temper.std.json.JsonSyntaxTree;
import temper.std.json.JsonSyntaxTreeProducer;
import temper.std.json.JsonTextProducer;
import temper.std.json.NullInterchangeContext;
import temper.std.json.OrNullJsonAdapter;

public final class JsonSyntaxTreeGlobal {
    private JsonSyntaxTreeGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("json_syntax_tree"));
        {
            List<String> list = List.<String>of("[]", " [] ", " [ ] ", "[1]", "null");
            int length = list.size();
            int i = 0;
            while (i < length) {
                String s = Core.listGet(list, i);
                i = i + 1;
                @Nullable JsonSyntaxTree jsonValue;
                try {
                    jsonValue = JsonGlobal.parseJson(s);
                } catch (RuntimeException ignored) {
                    jsonValue = null;
                }
                console.log("`" + s + "` is empty array: " + (jsonValue != null && jsonValue instanceof JsonArray && ((JsonArray) jsonValue).elements.isEmpty()));
            }
        }
        {
            List<String> list2 = List.<String>of("[", "]", "[,]", "[]]", "[1,]", "[1,,2]");
            int length2 = list2.size();
            int i2 = 0;
            while (i2 < length2) {
                String s2 = Core.listGet(list2, i2);
                i2 = i2 + 1;
                boolean bubbled = false;
                JsonSyntaxTreeProducer p = new JsonSyntaxTreeProducer();
                JsonGlobal.parseJsonToProducer(s2, p);
                try {
                    p.toJsonSyntaxTree();
                } catch (RuntimeException ignored2) {
                    bubbled = true;
                }
                @Nullable String error = p.getJsonError();
                console.log("`" + s2 + "` has" + (bubbled ? "" : " no") + " error: " + (error == null ? "MISSING ERROR" : error));
            }
        }
        String selectCodePoints = "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000e\u000f\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f -𐀀--퟿\u2028\u2029\u0085";
        {
            JsonTextProducer p2 = new JsonTextProducer(NullInterchangeContext.instance);
            p2.stringValue(selectCodePoints);
            String t;
            try {
                t = p2.toJsonString();
            } catch (RuntimeException ignored3) {
                t = "?!";
            }
            console.log("string with select code points: " + t);
        }
        {
            JsonTextProducer p3 = new JsonTextProducer(NullInterchangeContext.instance);
            p3.startObject();
            OrNullJsonAdapter<Integer> intOrNullAdapter = new OrNullJsonAdapter<Integer>(JsonGlobal.int32JsonAdapter());
            @Nullable Integer x = null;
            @Nullable Integer y = 123;
            p3.objectKey("x");
            intOrNullAdapter.encodeToJson(x, p3);
            p3.objectKey("y");
            intOrNullAdapter.encodeToJson(y, p3);
            p3.endObject();
            console.log(p3.toJsonString());
        }
        {
            String s3 = "{\"x\":null,\"y\":123,\"z\":123456789012}";
            JsonSyntaxTree ast = JsonGlobal.parseJson(s3);
            JsonObject obj = (JsonObject) ast;
            JsonSyntaxTree xAst = obj.propertyValueOrBubble("x");
            JsonSyntaxTree yAst = obj.propertyValueOrBubble("y");
            console.log("for x, got JSON null: " + (xAst instanceof JsonNull));
            console.log("for y, got JSON numeric: " + (yAst instanceof JsonNumeric));
            OrNullJsonAdapter<Integer> intOrNullAdapter2 = new OrNullJsonAdapter<Integer>(JsonGlobal.int32JsonAdapter());
            @Nullable Integer x2 = intOrNullAdapter2.decodeFromJson(xAst, NullInterchangeContext.instance);
            @Nullable Integer y2 = intOrNullAdapter2.decodeFromJson(yAst, NullInterchangeContext.instance);
            console.log("x ?? -1 -> " + (x2 == null ? -1 : x2));
            console.log("y ?? -1 -> " + (y2 == null ? -1 : y2));
            JsonSyntaxTree zAst = obj.propertyValueOrBubble("z");
            OrNullJsonAdapter<Long> int64OrNullAdapter = new OrNullJsonAdapter<Long>(JsonGlobal.int64JsonAdapter());
            @Nullable Long z = int64OrNullAdapter.decodeFromJson(zAst, NullInterchangeContext.instance);
            console.log("z ?? -1 -> " + Long.toString(z == null ? -1L : z));
        }
        JsonTextProducer normalJson = new JsonTextProducer();
        JsonGlobal.parseJsonToProducer("{\n  \"foo\": \"bar\",\n  \"baz\": [\n    \"boo\",\n    { \"f\": 1, \"a\": null, \"r\": 123.0 }\n  ]\n}\n", normalJson);
        console.log("Normalized:");
        console.log(normalJson.toJsonString());
    }
}
