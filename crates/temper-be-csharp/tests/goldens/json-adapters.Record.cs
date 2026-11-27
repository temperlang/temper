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

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    public static Record DecodeFromJson(IJsonSyntaxTree t, IInterchangeContext ic)
    {
        JsonObject obj;
        if (t is JsonObject)
        {
            obj = (JsonObject)t;
        }
        else
        {
            throw new JsonShapeException("expected Record JSON object");
        }
        IJsonSyntaxTree nameTree = obj.PropertyValueOrThrow("name");
        JsonString nameJson;
        if (nameTree is JsonString)
        {
            nameJson = (JsonString)nameTree;
        }
        else
        {
            throw new JsonShapeException("expected String for name");
        }
        string name = nameJson.Content;
        IJsonSyntaxTree activeTree = obj.PropertyValueOrThrow("is-active");
        JsonBoolean activeJson;
        if (activeTree is JsonBoolean)
        {
            activeJson = (JsonBoolean)activeTree;
        }
        else
        {
            throw new JsonShapeException("expected Boolean for is-active");
        }
        bool active = activeJson.Content;
        IJsonSyntaxTree scoreTree = obj.PropertyValueOrThrow("score");
        IJsonNumeric scoreJson;
        if (scoreTree is IJsonNumeric)
        {
            scoreJson = (IJsonNumeric)scoreTree;
        }
        else
        {
            throw new JsonShapeException("expected Float64 for score");
        }
        double score;
        try
        {
            score = scoreJson.AsFloat64();
        }
        catch (ParseError failure)
        {
            throw Core.WithCause(new JsonShapeException("expected Float64 for score"), failure);
        }
        IJsonSyntaxTree bigTree = obj.PropertyValueOrThrow("big");
        IJsonNumeric bigJson;
        if (bigTree is IJsonNumeric)
        {
            bigJson = (IJsonNumeric)bigTree;
        }
        else
        {
            throw new JsonShapeException("expected Int64 for big");
        }
        long big;
        try
        {
            big = bigJson.AsInt64();
        }
        catch (ParseError failure2)
        {
            throw Core.WithCause(new JsonShapeException("expected Int64 for big"), failure2);
        }
        string? nickname = Optional.OrNull<string>(new OrNullJsonAdapter<string>(JsonGlobal.StringJsonAdapter()).DecodeFromJson(obj.PropertyValueOrThrow("nickname"), ic));
        IReadOnlyList<Point> points = JsonGlobal.ListJsonAdapter(Point.JsonAdapter()).DecodeFromJson(obj.PropertyValueOrThrow("points"), ic);
        return new Record(name, active, score, big, nickname, points);
    }

    public static IJsonAdapter<Record> JsonAdapter()
    {
        return new RecordJsonAdapter();
    }
}
