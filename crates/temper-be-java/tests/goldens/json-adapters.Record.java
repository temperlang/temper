package json_adapters;

import java.util.List;
import temper.core.Nullable;
import temper.std.json.InterchangeContext;
import temper.std.json.JsonAdapter;
import temper.std.json.JsonBoolean;
import temper.std.json.JsonGlobal;
import temper.std.json.JsonNumeric;
import temper.std.json.JsonObject;
import temper.std.json.JsonProducer;
import temper.std.json.JsonString;
import temper.std.json.JsonSyntaxTree;
import temper.std.json.OrNullJsonAdapter;

final class Record {
    public final String name;
    public final boolean active;
    public final double score;
    public final long big;
    public final @Nullable String nickname;
    public final List<Point> points;

    public Record(String name, boolean active, double score, long big, @Nullable String nickname, List<Point> points) {
        this.name = name;
        this.active = active;
        this.score = score;
        this.big = big;
        this.nickname = nickname;
        this.points = points;
    }

    public void encodeToJson(JsonProducer p) {
        p.startObject();
        p.objectKey("name");
        p.stringValue(this.name);
        p.objectKey("is-active");
        p.booleanValue(this.active);
        p.objectKey("score");
        p.float64Value(this.score);
        p.objectKey("big");
        p.int64Value(this.big);
        p.objectKey("nickname");
        new OrNullJsonAdapter<String>(JsonGlobal.stringJsonAdapter()).encodeToJson(this.nickname, p);
        p.objectKey("points");
        JsonGlobal.listJsonAdapter(Point.jsonAdapter()).encodeToJson(this.points, p);
        p.endObject();
    }

    public static Record decodeFromJson(JsonSyntaxTree t, InterchangeContext ic) {
        JsonObject obj = (JsonObject) t;
        JsonString nameJson = (JsonString) obj.propertyValueOrBubble("name");
        String name = nameJson.content;
        JsonBoolean activeJson = (JsonBoolean) obj.propertyValueOrBubble("is-active");
        boolean active = activeJson.content;
        JsonNumeric scoreJson = (JsonNumeric) obj.propertyValueOrBubble("score");
        double score = scoreJson.asFloat64();
        JsonNumeric bigJson = (JsonNumeric) obj.propertyValueOrBubble("big");
        long big = bigJson.asInt64();
        @Nullable String nickname = new OrNullJsonAdapter<String>(JsonGlobal.stringJsonAdapter()).decodeFromJson(obj.propertyValueOrBubble("nickname"), ic);
        List<Point> points = JsonGlobal.listJsonAdapter(Point.jsonAdapter()).decodeFromJson(obj.propertyValueOrBubble("points"), ic);
        return new Record(name, active, score, big, nickname, points);
    }

    public static JsonAdapter<Record> jsonAdapter() {
        return new RecordJsonAdapter();
    }

    public String getName() {
        return this.name;
    }

    public boolean isActive() {
        return this.active;
    }

    public double getScore() {
        return this.score;
    }

    public long getBig() {
        return this.big;
    }

    public @Nullable String getNickname() {
        return this.nickname;
    }

    public List<Point> getPoints() {
        return this.points;
    }

    public static final class Builder {
        String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        boolean active;
        boolean active__set;

        public Builder active(boolean active) {
            active__set = true;
            this.active = active;
            return this;
        }

        double score;
        boolean score__set;

        public Builder score(double score) {
            score__set = true;
            this.score = score;
            return this;
        }

        long big;
        boolean big__set;

        public Builder big(long big) {
            big__set = true;
            this.big = big;
            return this;
        }

        @Nullable String nickname;
        boolean nickname__set;

        public Builder nickname(@Nullable String nickname) {
            nickname__set = true;
            this.nickname = nickname;
            return this;
        }

        List<Point> points;

        public Builder points(List<Point> points) {
            this.points = points;
            return this;
        }

        public Record build() {
            if (name == null || !active__set || !score__set || !big__set || !nickname__set || points == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (name == null) {
                    _message.append(" name");
                }
                if (!active__set) {
                    _message.append(" active");
                }
                if (!score__set) {
                    _message.append(" score");
                }
                if (!big__set) {
                    _message.append(" big");
                }
                if (!nickname__set) {
                    _message.append(" nickname");
                }
                if (points == null) {
                    _message.append(" points");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Record(name, active, score, big, nickname, points);
        }
    }
}
