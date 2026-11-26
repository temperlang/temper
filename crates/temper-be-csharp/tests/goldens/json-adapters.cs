using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonAdapters;

public static class JsonAdaptersGlobal
{
    internal static readonly ILoggingConsole console;

    internal static string Encode<T>(IJsonAdapter<T> adapter, T value)
    {
        JsonTextProducer p = new JsonTextProducer();
        adapter.EncodeToJson(value, p);
        try
        {
            return p.ToJsonString();
        }
        catch (JsonSyntaxException)
        {
            return "bad JSON";
        }
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    internal static T Decode<T>(IJsonAdapter<T> adapter, string text)
    {
        return adapter.DecodeFromJson(JsonGlobal.ParseJson(text), NullInterchangeContext.Instance);
    }

    internal static void RoundTrip<T>(IJsonAdapter<T> adapter, T value)
    {
        string text = Encode(adapter, value);
        T back;
        try
        {
            back = Decode(adapter, text);
        }
        catch (JsonException bubble1)
        {
            throw Core.Panic("panic", bubble1);
        }
        string again = Encode(adapter, back);
        console.Log(again == text ? text : text + " became " + again);
    }

    static JsonAdaptersGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("JsonAdapters");
        RoundTrip(Point.JsonAdapter(), new Point(1, -2));
        RoundTrip(Record.JsonAdapter(), new Record("Ada", true, 1.5, 123456789012L, null, Listed.CreateReadOnlyList<Point>(new Point(3, 4))));
        IReadOnlyList<IShape> shapes = Listed.CreateReadOnlyList<IShape>(new Circle(2), new Rect(3, 4));
        RoundTrip(JsonGlobal.ListJsonAdapter(IShape.JsonAdapter()), shapes);
        IReadOnlyList<IMessage> messages = Listed.CreateReadOnlyList<IMessage>(new Hello("hi"), new Bye("so long"));
        RoundTrip(JsonGlobal.ListJsonAdapter(IMessage.JsonAdapter()), messages);
        RoundTrip(Celsius.JsonAdapter(), new Celsius(21));
        IShape shape;
        try
        {
            shape = Decode(IShape.JsonAdapter(), "{\"height\": 6, \"width\": 5}");
        }
        catch (JsonException bubble2)
        {
            throw Core.Panic("panic", bubble2);
        }
        {
            if (shape is Rect)
            {
                console.Log(string.Create(CultureInfo.InvariantCulture, $"rect {((Rect)shape).Width}x{((Rect)shape).Height}"));
            }
            else
            {
                console.Log("not a rect");
            }
        }
        Record record;
        try
        {
            record = Decode(Record.JsonAdapter(), "{\"name\": \"Bo\", \"is-active\": false, \"score\": 2, \"big\": 7, \"nickname\": \"B\", \"points\": []}");
        }
        catch (JsonException bubble3)
        {
            throw Core.Panic("panic", bubble3);
        }
        string @string = record.Name + " " + (record.Active ? "true" : "false") + " ";
        string? subject1 = record.Nickname;
        console.Log(@string + (subject1 == null ? "none" : subject1) + " " + record.Points.Count.ToString(CultureInfo.InvariantCulture));
        string t2;
        try
        {
            Decode(Point.JsonAdapter(), "{\"x\": 1}");
            t2 = "decoded";
        }
        catch (JsonException e)
        {
            t2 = e.Message;
        }
        console.Log(t2);
        string t3;
        try
        {
            Decode(IMessage.JsonAdapter(), "{\"kind\": \"what\", \"text\": \"?\"}");
            t3 = "decoded";
        }
        catch (JsonException e2)
        {
            t3 = e2.Message;
        }
        console.Log(t3);
    }
}
