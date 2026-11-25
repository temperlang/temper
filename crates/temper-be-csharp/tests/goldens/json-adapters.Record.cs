using System.Collections.Generic;
using TemperLang.Core;
using TemperLang.Std.Json;

namespace JsonAdapters;

internal class Record
{
    private readonly string name;
    private readonly bool active;
    private readonly double score;
    private readonly long big;
    private readonly string? nickname;
    private readonly IReadOnlyList<Point> points;

    public Record(string name, bool active, double score, long big, string? nickname, IReadOnlyList<Point> points)
    {
        this.name = name;
        this.active = active;
        this.score = score;
        this.big = big;
        this.nickname = nickname;
        this.points = points;
    }

    public string Name
    {
        get
        {
            return this.name;
        }
    }

    public bool Active
    {
        get
        {
            return this.active;
        }
    }

    public double Score
    {
        get
        {
            return this.score;
        }
    }

    public long Big
    {
        get
        {
            return this.big;
        }
    }

    public string? Nickname
    {
        get
        {
            return this.nickname;
        }
    }

    public IReadOnlyList<Point> Points
    {
        get
        {
            return this.points;
        }
    }

    public void EncodeToJson(IJsonProducer p)
    {
        p.StartObject();
        p.ObjectKey("name");
        p.StringValue(this.name);
        p.ObjectKey("is-active");
        p.BooleanValue(this.active);
        p.ObjectKey("score");
        p.Float64_Value(this.score);
        p.ObjectKey("big");
        p.Int64_Value(this.big);
        p.ObjectKey("nickname");
        new OrNullJsonAdapter<string>(JsonGlobal.StringJsonAdapter()).EncodeToJson(Optional.Of<string>(this.nickname), p);
        p.ObjectKey("points");
        JsonGlobal.ListJsonAdapter(Point.JsonAdapter()).EncodeToJson(this.points, p);
        p.EndObject();
    }

    public static Record DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj = t is JsonObject ? (JsonObject)t : Core.Bubble<JsonObject>();
        IJsonSyntaxTree nameTree = obj.PropertyValueOrBubble("name");
        JsonString nameJson = nameTree is JsonString ? (JsonString)nameTree : Core.Bubble<JsonString>();
        string name = nameJson.Content;
        IJsonSyntaxTree activeTree = obj.PropertyValueOrBubble("is-active");
        JsonBoolean activeJson = activeTree is JsonBoolean ? (JsonBoolean)activeTree : Core.Bubble<JsonBoolean>();
        bool active = activeJson.Content;
        IJsonSyntaxTree scoreTree = obj.PropertyValueOrBubble("score");
        IJsonNumeric scoreJson = scoreTree is IJsonNumeric ? (IJsonNumeric)scoreTree : Core.Bubble<IJsonNumeric>();
        double score = scoreJson.AsFloat64();
        IJsonSyntaxTree bigTree = obj.PropertyValueOrBubble("big");
        IJsonNumeric bigJson = bigTree is IJsonNumeric ? (IJsonNumeric)bigTree : Core.Bubble<IJsonNumeric>();
        long big = bigJson.AsInt64();
        string? nickname = Optional.OrNull<string>(new OrNullJsonAdapter<string>(JsonGlobal.StringJsonAdapter()).DecodeFromJson(obj.PropertyValueOrBubble("nickname"), ic));
        IReadOnlyList<Point> points = JsonGlobal.ListJsonAdapter(Point.JsonAdapter()).DecodeFromJson(obj.PropertyValueOrBubble("points"), ic);
        return new Record(name, active, score, big, nickname, points);
    }

    public static IJsonAdapter<Record> JsonAdapter()
    {
        return new RecordJsonAdapter();
    }
}
