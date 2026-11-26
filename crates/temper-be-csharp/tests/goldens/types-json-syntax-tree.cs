using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonSyntaxTree;

public static class JsonSyntaxTreeGlobal
{
    static JsonSyntaxTreeGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("JsonSyntaxTree");
        {
            IReadOnlyList<string> list = Listed.CreateReadOnlyList<string>("[]", " [] ", " [ ] ", "[1]", "null");
            int length = list.Count;
            int i = 0;
            while (i < length)
            {
                string s = Listed.Get(list, i);
                i = i + 1;
                IJsonSyntaxTree? jsonValue;
                try
                {
                    jsonValue = JsonGlobal.ParseJson(s);
                }
                catch (JsonSyntaxException)
                {
                    jsonValue = null;
                }
                console.Log("`" + s + "` is empty array: " + (jsonValue != null && jsonValue is JsonArray && ((JsonArray)jsonValue).Elements.Count == 0 ? "true" : "false"));
            }
        }
        {
            IReadOnlyList<string> list2 = Listed.CreateReadOnlyList<string>("[", "]", "[,]", "[]]", "[1,]", "[1,,2]");
            int length2 = list2.Count;
            int i2 = 0;
            while (i2 < length2)
            {
                string s2 = Listed.Get(list2, i2);
                i2 = i2 + 1;
                bool failed = false;
                JsonSyntaxTreeProducer p = new JsonSyntaxTreeProducer();
                JsonGlobal.ParseJsonToProducer(s2, p);
                try
                {
                    p.ToJsonSyntaxTree();
                }
                catch (JsonSyntaxException)
                {
                    failed = true;
                }
                string? error = p.JsonError;
                console.Log("`" + s2 + "` has" + (failed ? "" : " no") + " error: " + (error == null ? "MISSING ERROR" : error));
            }
        }
        {
            IReadOnlyList<string> list3 = Listed.CreateReadOnlyList<string>("[1,]", "\"a\"", "7");
            int length3 = list3.Count;
            int i3 = 0;
            while (i3 < length3)
            {
                string s3 = Listed.Get(list3, i3);
                i3 = i3 + 1;
                string decoded;
                try
                {
                    IJsonSyntaxTree tree = JsonGlobal.ParseJson(s3);
                    decoded = JsonGlobal.Int32_JsonAdapter().DecodeFromJson(tree, NullInterchangeContext.Instance).ToString(CultureInfo.InvariantCulture);
                }
                catch (JsonException e)
                {
                    decoded = e.Message;
                }
                console.Log(decoded);
            }
        }
        string selectCodePoints = "\0\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\t\n\u000B\u000C\r\u000E\u000F\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F -𐀀-\uE000-퟿\u2028\u2029\u0085";
        {
            JsonTextProducer p2 = new JsonTextProducer(NullInterchangeContext.Instance);
            p2.StringValue(selectCodePoints);
            string t1;
            try
            {
                t1 = p2.ToJsonString();
            }
            catch (JsonSyntaxException)
            {
                t1 = "?!";
            }
            console.Log("string with select code points: " + t1);
        }
        {
            JsonTextProducer p3 = new JsonTextProducer(NullInterchangeContext.Instance);
            p3.StartObject();
            OrNullJsonAdapter<int> intOrNullAdapter = new OrNullJsonAdapter<int>(JsonGlobal.Int32_JsonAdapter());
            int? x = null;
            int? y = 123;
            p3.ObjectKey("x");
            intOrNullAdapter.EncodeToJson(Optional.Of<int>(x), p3);
            p3.ObjectKey("y");
            intOrNullAdapter.EncodeToJson(Optional.Of<int>(y), p3);
            p3.EndObject();
            console.Log(p3.ToJsonString());
        }
        {
            string s4 = "{\"x\":null,\"y\":123,\"z\":123456789012}";
            IJsonSyntaxTree ast = JsonGlobal.ParseJson(s4);
            JsonObject obj = Core.Cast<JsonObject>(ast);
            IJsonSyntaxTree xAst = obj.PropertyValueOrBubble("x");
            IJsonSyntaxTree yAst = obj.PropertyValueOrBubble("y");
            console.Log("for x, got JSON null: " + (xAst is JsonNull ? "true" : "false"));
            console.Log("for y, got JSON numeric: " + (yAst is IJsonNumeric ? "true" : "false"));
            OrNullJsonAdapter<int> intOrNullAdapter2 = new OrNullJsonAdapter<int>(JsonGlobal.Int32_JsonAdapter());
            int? x2 = Optional.ToNullable<int>(intOrNullAdapter2.DecodeFromJson(xAst, NullInterchangeContext.Instance));
            int? y2 = Optional.ToNullable<int>(intOrNullAdapter2.DecodeFromJson(yAst, NullInterchangeContext.Instance));
            console.Log("x ?? -1 -> " + (x2 == null ? -1 : x2.Value).ToString(CultureInfo.InvariantCulture));
            console.Log("y ?? -1 -> " + (y2 == null ? -1 : y2.Value).ToString(CultureInfo.InvariantCulture));
            IJsonSyntaxTree zAst = obj.PropertyValueOrBubble("z");
            OrNullJsonAdapter<long> int64OrNullAdapter = new OrNullJsonAdapter<long>(JsonGlobal.Int64_JsonAdapter());
            long? z = Optional.ToNullable<long>(int64OrNullAdapter.DecodeFromJson(zAst, NullInterchangeContext.Instance));
            console.Log("z ?? -1 -> " + (z == null ? -1L : z.Value).ToString(CultureInfo.InvariantCulture));
        }
        JsonTextProducer normalJson = new JsonTextProducer();
        JsonGlobal.ParseJsonToProducer("{\n  \"foo\": \"bar\",\n  \"baz\": [\n    \"boo\",\n    { \"f\": 1, \"a\": null, \"r\": 123.0 }\n  ]\n}\n", normalJson);
        console.Log("Normalized:");
        console.Log(normalJson.ToJsonString());
    }
}
