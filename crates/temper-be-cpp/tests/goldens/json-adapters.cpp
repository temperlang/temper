// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/json.hpp>

namespace json_adapters {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct PointJsonAdapter;
struct Point;
struct RecordJsonAdapter;
struct Record;
struct CircleJsonAdapter;
struct Circle;
struct RectJsonAdapter;
struct Rect;
struct ShapeJsonAdapter;
struct Shape;
struct HelloJsonAdapter;
struct Hello;
struct ByeJsonAdapter;
struct Bye;
struct MessageJsonAdapter;
struct Message;
struct CelsiusJsonAdapter;
struct Celsius;

struct PointJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Point>> {
  static std::shared_ptr<PointJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Point> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Point> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct Point : public std::enable_shared_from_this<Point> {
  int32_t x;
  int32_t y;
  static std::shared_ptr<Point> make(int32_t x, int32_t y);
  void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const;
  static std::shared_ptr<Point> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Point>>> jsonAdapter();
  int32_t get_x() const;
  int32_t get_y() const;
};

struct RecordJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Record>> {
  static std::shared_ptr<RecordJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Record> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Record> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct Record : public std::enable_shared_from_this<Record> {
  temper::core::Str name;
  bool active;
  double score;
  int64_t big;
  temper::core::NullableParam<temper::core::Str> nickname;
  std::shared_ptr<std::vector<std::shared_ptr<Point>>> points;
  static std::shared_ptr<Record> make(temper::core::Str const & name, bool active, double score, int64_t big, temper::core::NullableParam<temper::core::Str> nickname, std::shared_ptr<std::vector<std::shared_ptr<Point>>> const & points);
  void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const;
  static std::shared_ptr<Record> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Record>>> jsonAdapter();
  temper::core::Str get_name() const;
  bool get_active() const;
  double get_score() const;
  int64_t get_big() const;
  temper::core::NullableParam<temper::core::Str> get_nickname() const;
  std::shared_ptr<std::vector<std::shared_ptr<Point>>> get_points() const;
};

struct CircleJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Circle>> {
  static std::shared_ptr<CircleJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Circle> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Circle> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct Shape : public virtual temper::core::AnyValueBase {
  virtual void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const = 0;
  static std::shared_ptr<Shape> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Shape>>> jsonAdapter();
};

struct Circle : public virtual Shape {
  int32_t radius;
  static std::shared_ptr<Circle> make(int32_t radius);
  void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  static std::shared_ptr<Circle> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Circle>>> jsonAdapter();
  int32_t get_radius() const;
};

struct RectJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Rect>> {
  static std::shared_ptr<RectJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Rect> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Rect> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct Rect : public virtual Shape {
  int32_t width;
  int32_t height;
  static std::shared_ptr<Rect> make(int32_t width, int32_t height);
  void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  static std::shared_ptr<Rect> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Rect>>> jsonAdapter();
  int32_t get_width() const;
  int32_t get_height() const;
};

struct ShapeJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Shape>> {
  static std::shared_ptr<ShapeJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Shape> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Shape> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct HelloJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Hello>> {
  static std::shared_ptr<HelloJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Hello> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Hello> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct Message : public virtual temper::core::AnyValueBase {
  virtual void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const = 0;
  static std::shared_ptr<Message> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Message>>> jsonAdapter();
};

struct Hello : public virtual Message {
  temper::core::Str text;
  static std::shared_ptr<Hello> make(temper::core::Str const & text);
  void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  static std::shared_ptr<Hello> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Hello>>> jsonAdapter();
  temper::core::Str get_text() const;
};

struct ByeJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Bye>> {
  static std::shared_ptr<ByeJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Bye> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Bye> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct Bye : public virtual Message {
  temper::core::Str text;
  static std::shared_ptr<Bye> make(temper::core::Str const & text);
  void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  static std::shared_ptr<Bye> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Bye>>> jsonAdapter();
  temper::core::Str get_text() const;
};

struct MessageJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Message>> {
  static std::shared_ptr<MessageJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Message> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Message> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct CelsiusJsonAdapter : public virtual temper_std::JsonAdapter<std::shared_ptr<Celsius>> {
  static std::shared_ptr<CelsiusJsonAdapter> make();
  void encodeToJson(std::shared_ptr<Celsius> x, std::shared_ptr<temper_std::JsonProducer> const & p) const override;
  std::shared_ptr<Celsius> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const override;
};

struct Celsius : public std::enable_shared_from_this<Celsius> {
  int32_t degrees;
  static std::shared_ptr<Celsius> make(int32_t degrees);
  void encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const;
  static std::shared_ptr<Celsius> decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic);
  static std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Celsius>>> jsonAdapter();
  int32_t get_degrees() const;
};

}  // namespace json_adapters

// init.cpp
#include <json-adapters/init.hpp>

namespace json_adapters {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<PointJsonAdapter> PointJsonAdapter::make() {
  std::shared_ptr<PointJsonAdapter> self = std::make_shared<PointJsonAdapter>();
  return self;
}

void PointJsonAdapter::encodeToJson(std::shared_ptr<Point> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Point> PointJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Point::decodeFromJson(t, ic);
}

std::shared_ptr<Point> Point::make(int32_t x, int32_t y) {
  std::shared_ptr<Point> self = std::make_shared<Point>();
  self->x = x;
  self->y = y;
  return self;
}

void Point::encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const {
  p->startObject();
  p->objectKey(TEMPER_STR("x"));
  p->int32Value(this->x);
  p->objectKey(TEMPER_STR("y"));
  p->int32Value(this->y);
  p->endObject();
}

std::shared_ptr<Point> Point::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const &) {
  std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
  std::shared_ptr<temper_std::JsonNumeric> xJson = core::checked_cast<temper_std::JsonNumeric>(obj->propertyValueOrBubble(TEMPER_STR("x")));
  int32_t x = xJson->asInt32();
  std::shared_ptr<temper_std::JsonNumeric> yJson = core::checked_cast<temper_std::JsonNumeric>(obj->propertyValueOrBubble(TEMPER_STR("y")));
  int32_t y = yJson->asInt32();
  return Point::make(x, y);
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Point>>> Point::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Point>>>(PointJsonAdapter::make());
}

int32_t Point::get_x() const {
  return this->x;
}

int32_t Point::get_y() const {
  return this->y;
}

std::shared_ptr<RecordJsonAdapter> RecordJsonAdapter::make() {
  std::shared_ptr<RecordJsonAdapter> self = std::make_shared<RecordJsonAdapter>();
  return self;
}

void RecordJsonAdapter::encodeToJson(std::shared_ptr<Record> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Record> RecordJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Record::decodeFromJson(t, ic);
}

std::shared_ptr<Record> Record::make(core::Str const & name, bool active, double score, int64_t big, core::NullableParam<core::Str> nickname, std::shared_ptr<std::vector<std::shared_ptr<Point>>> const & points) {
  std::shared_ptr<Record> self = std::make_shared<Record>();
  self->name = name;
  self->active = active;
  self->score = score;
  self->big = big;
  self->nickname = nickname;
  self->points = points;
  return self;
}

void Record::encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const {
  std::shared_ptr<Record const> self = shared_from_this();
  p->startObject();
  p->objectKey(TEMPER_STR("name"));
  p->stringValue(self->name);
  p->objectKey(TEMPER_STR("is-active"));
  p->booleanValue(self->active);
  p->objectKey(TEMPER_STR("score"));
  p->float64Value(self->score);
  p->objectKey(TEMPER_STR("big"));
  p->int64Value(self->big);
  p->objectKey(TEMPER_STR("nickname"));
  temper_std::OrNullJsonAdapter<core::Str>::make(temper_std::stringJsonAdapter())->encodeToJson(self->nickname, p);
  p->objectKey(TEMPER_STR("points"));
  temper_std::listJsonAdapter<std::shared_ptr<Point>>(Point::jsonAdapter())->encodeToJson(self->points, p);
  p->endObject();
}

std::shared_ptr<Record> Record::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) {
  std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
  std::shared_ptr<temper_std::JsonString> nameJson = core::checked_cast<temper_std::JsonString>(obj->propertyValueOrBubble(TEMPER_STR("name")));
  core::Str name = nameJson->content;
  std::shared_ptr<temper_std::JsonBoolean> activeJson = core::checked_cast<temper_std::JsonBoolean>(obj->propertyValueOrBubble(TEMPER_STR("is-active")));
  bool active = activeJson->content;
  std::shared_ptr<temper_std::JsonNumeric> scoreJson = core::checked_cast<temper_std::JsonNumeric>(obj->propertyValueOrBubble(TEMPER_STR("score")));
  double score = scoreJson->asFloat64();
  std::shared_ptr<temper_std::JsonNumeric> bigJson = core::checked_cast<temper_std::JsonNumeric>(obj->propertyValueOrBubble(TEMPER_STR("big")));
  int64_t big = bigJson->asInt64();
  std::shared_ptr<temper_std::OrNullJsonAdapter<core::Str>> orNullJsonAdapter = temper_std::OrNullJsonAdapter<core::Str>::make(temper_std::stringJsonAdapter());
  core::NullableParam<core::Str> nickname = orNullJsonAdapter->decodeFromJson(obj->propertyValueOrBubble(TEMPER_STR("nickname")), ic);
  std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<std::vector<std::shared_ptr<Point>>>>> value2 = temper_std::listJsonAdapter<std::shared_ptr<Point>>(Point::jsonAdapter());
  std::shared_ptr<std::vector<std::shared_ptr<Point>>> points = value2->decodeFromJson(obj->propertyValueOrBubble(TEMPER_STR("points")), ic);
  return Record::make(name, active, score, big, nickname, points);
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Record>>> Record::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Record>>>(RecordJsonAdapter::make());
}

core::Str Record::get_name() const {
  return this->name;
}

bool Record::get_active() const {
  return this->active;
}

double Record::get_score() const {
  return this->score;
}

int64_t Record::get_big() const {
  return this->big;
}

core::NullableParam<core::Str> Record::get_nickname() const {
  return this->nickname;
}

std::shared_ptr<std::vector<std::shared_ptr<Point>>> Record::get_points() const {
  return this->points;
}

std::shared_ptr<CircleJsonAdapter> CircleJsonAdapter::make() {
  std::shared_ptr<CircleJsonAdapter> self = std::make_shared<CircleJsonAdapter>();
  return self;
}

void CircleJsonAdapter::encodeToJson(std::shared_ptr<Circle> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Circle> CircleJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Circle::decodeFromJson(t, ic);
}

std::shared_ptr<Circle> Circle::make(int32_t radius) {
  std::shared_ptr<Circle> self = std::make_shared<Circle>();
  self->radius = radius;
  return self;
}

void Circle::encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const {
  p->startObject();
  p->objectKey(TEMPER_STR("radius"));
  p->int32Value(this->radius);
  p->endObject();
}

std::shared_ptr<Circle> Circle::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const &) {
  std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
  std::shared_ptr<temper_std::JsonNumeric> radiusJson = core::checked_cast<temper_std::JsonNumeric>(obj->propertyValueOrBubble(TEMPER_STR("radius")));
  int32_t radius = radiusJson->asInt32();
  return Circle::make(radius);
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Circle>>> Circle::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Circle>>>(CircleJsonAdapter::make());
}

int32_t Circle::get_radius() const {
  return this->radius;
}

std::shared_ptr<RectJsonAdapter> RectJsonAdapter::make() {
  std::shared_ptr<RectJsonAdapter> self = std::make_shared<RectJsonAdapter>();
  return self;
}

void RectJsonAdapter::encodeToJson(std::shared_ptr<Rect> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Rect> RectJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Rect::decodeFromJson(t, ic);
}

std::shared_ptr<Rect> Rect::make(int32_t width, int32_t height) {
  std::shared_ptr<Rect> self = std::make_shared<Rect>();
  self->width = width;
  self->height = height;
  return self;
}

void Rect::encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const {
  p->startObject();
  p->objectKey(TEMPER_STR("width"));
  p->int32Value(this->width);
  p->objectKey(TEMPER_STR("height"));
  p->int32Value(this->height);
  p->endObject();
}

std::shared_ptr<Rect> Rect::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const &) {
  std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
  std::shared_ptr<temper_std::JsonNumeric> widthJson = core::checked_cast<temper_std::JsonNumeric>(obj->propertyValueOrBubble(TEMPER_STR("width")));
  int32_t width = widthJson->asInt32();
  std::shared_ptr<temper_std::JsonNumeric> heightJson = core::checked_cast<temper_std::JsonNumeric>(obj->propertyValueOrBubble(TEMPER_STR("height")));
  int32_t height = heightJson->asInt32();
  return Rect::make(width, height);
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Rect>>> Rect::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Rect>>>(RectJsonAdapter::make());
}

int32_t Rect::get_width() const {
  return this->width;
}

int32_t Rect::get_height() const {
  return this->height;
}

std::shared_ptr<ShapeJsonAdapter> ShapeJsonAdapter::make() {
  std::shared_ptr<ShapeJsonAdapter> self = std::make_shared<ShapeJsonAdapter>();
  return self;
}

void ShapeJsonAdapter::encodeToJson(std::shared_ptr<Shape> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Shape> ShapeJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Shape::decodeFromJson(t, ic);
}

std::shared_ptr<Shape> Shape::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) {
  {
    std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
    std::shared_ptr<temper_std::JsonSyntaxTree> valueForRadius = obj->propertyValueOrNull(TEMPER_STR("radius"));
    {
      if (valueForRadius != nullptr) {
        return core::to_base<Shape>(Circle::jsonAdapter()->decodeFromJson(core::to_base<temper_std::JsonSyntaxTree>(obj), ic));
      }
      if (valueForRadius == nullptr) {
        return core::to_base<Shape>(Rect::jsonAdapter()->decodeFromJson(core::to_base<temper_std::JsonSyntaxTree>(obj), ic));
      }
    }
    core::bubble();
  }
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Shape>>> Shape::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Shape>>>(ShapeJsonAdapter::make());
}

std::shared_ptr<HelloJsonAdapter> HelloJsonAdapter::make() {
  std::shared_ptr<HelloJsonAdapter> self = std::make_shared<HelloJsonAdapter>();
  return self;
}

void HelloJsonAdapter::encodeToJson(std::shared_ptr<Hello> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Hello> HelloJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Hello::decodeFromJson(t, ic);
}

std::shared_ptr<Hello> Hello::make(core::Str const & text) {
  std::shared_ptr<Hello> self = std::make_shared<Hello>();
  self->text = text;
  return self;
}

void Hello::encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const {
  p->startObject();
  p->objectKey(TEMPER_STR("text"));
  p->stringValue(this->text);
  p->objectKey(TEMPER_STR("kind"));
  p->stringValue(TEMPER_STR("hello"));
  p->endObject();
}

std::shared_ptr<Hello> Hello::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const &) {
  std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
  std::shared_ptr<temper_std::JsonString> textJson = core::checked_cast<temper_std::JsonString>(obj->propertyValueOrBubble(TEMPER_STR("text")));
  core::Str text = textJson->content;
  return Hello::make(text);
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Hello>>> Hello::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Hello>>>(HelloJsonAdapter::make());
}

core::Str Hello::get_text() const {
  return this->text;
}

std::shared_ptr<ByeJsonAdapter> ByeJsonAdapter::make() {
  std::shared_ptr<ByeJsonAdapter> self = std::make_shared<ByeJsonAdapter>();
  return self;
}

void ByeJsonAdapter::encodeToJson(std::shared_ptr<Bye> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Bye> ByeJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Bye::decodeFromJson(t, ic);
}

std::shared_ptr<Bye> Bye::make(core::Str const & text) {
  std::shared_ptr<Bye> self = std::make_shared<Bye>();
  self->text = text;
  return self;
}

void Bye::encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const {
  p->startObject();
  p->objectKey(TEMPER_STR("text"));
  p->stringValue(this->text);
  p->objectKey(TEMPER_STR("kind"));
  p->stringValue(TEMPER_STR("bye"));
  p->endObject();
}

std::shared_ptr<Bye> Bye::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const &) {
  std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
  std::shared_ptr<temper_std::JsonString> textJson = core::checked_cast<temper_std::JsonString>(obj->propertyValueOrBubble(TEMPER_STR("text")));
  core::Str text = textJson->content;
  return Bye::make(text);
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Bye>>> Bye::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Bye>>>(ByeJsonAdapter::make());
}

core::Str Bye::get_text() const {
  return this->text;
}

std::shared_ptr<MessageJsonAdapter> MessageJsonAdapter::make() {
  std::shared_ptr<MessageJsonAdapter> self = std::make_shared<MessageJsonAdapter>();
  return self;
}

void MessageJsonAdapter::encodeToJson(std::shared_ptr<Message> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Message> MessageJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Message::decodeFromJson(t, ic);
}

std::shared_ptr<Message> Message::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) {
  {
    std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(t);
    std::shared_ptr<temper_std::JsonSyntaxTree> valueForKind = obj->propertyValueOrNull(TEMPER_STR("kind"));
    {
      if (valueForKind != nullptr && dynamic_cast<temper_std::JsonString const *>(valueForKind.get()) != nullptr) {
        core::Str subject = core::checked_cast<temper_std::JsonString>(valueForKind)->content;
        if (subject == TEMPER_STR("hello")) {
          return core::to_base<Message>(Hello::jsonAdapter()->decodeFromJson(core::to_base<temper_std::JsonSyntaxTree>(obj), ic));
        } else if (subject == TEMPER_STR("bye")) {
          return core::to_base<Message>(Bye::jsonAdapter()->decodeFromJson(core::to_base<temper_std::JsonSyntaxTree>(obj), ic));
        }
      }
    }
    core::bubble();
  }
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Message>>> Message::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Message>>>(MessageJsonAdapter::make());
}

std::shared_ptr<CelsiusJsonAdapter> CelsiusJsonAdapter::make() {
  std::shared_ptr<CelsiusJsonAdapter> self = std::make_shared<CelsiusJsonAdapter>();
  return self;
}

void CelsiusJsonAdapter::encodeToJson(std::shared_ptr<Celsius> x, std::shared_ptr<temper_std::JsonProducer> const & p) const {
  x->encodeToJson(p);
}

std::shared_ptr<Celsius> CelsiusJsonAdapter::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const & ic) const {
  return Celsius::decodeFromJson(t, ic);
}

std::shared_ptr<Celsius> Celsius::make(int32_t degrees) {
  std::shared_ptr<Celsius> self = std::make_shared<Celsius>();
  self->degrees = degrees;
  return self;
}

void Celsius::encodeToJson(std::shared_ptr<temper_std::JsonProducer> const & p) const {
  p->int32Value(this->degrees);
}

std::shared_ptr<Celsius> Celsius::decodeFromJson(std::shared_ptr<temper_std::JsonSyntaxTree> const & t, std::shared_ptr<temper_std::InterchangeContext> const &) {
  return Celsius::make(core::checked_cast<temper_std::JsonNumeric>(t)->asInt32());
}

std::shared_ptr<temper_std::JsonAdapter<std::shared_ptr<Celsius>>> Celsius::jsonAdapter() {
  return core::to_base<temper_std::JsonAdapter<std::shared_ptr<Celsius>>>(CelsiusJsonAdapter::make());
}

int32_t Celsius::get_degrees() const {
  return this->degrees;
}

template <class T>
static core::Str encode(std::shared_ptr<temper_std::JsonAdapter<T>> const & adapter, T value) {
  std::shared_ptr<temper_std::JsonTextProducer> p = temper_std::JsonTextProducer::make();
  adapter->encodeToJson(value, core::to_base<temper_std::JsonProducer>(p));
  try {
    return p->toJsonString();
  } catch (core::TemperBubble const &) {
    return TEMPER_STR("bad JSON");
  }
}

template <class T>
static T decode(std::shared_ptr<temper_std::JsonAdapter<T>> const & adapter, core::Str const & text) {
  return adapter->decodeFromJson(temper_std::parseJson(text), core::to_base<temper_std::InterchangeContext>(temper_std::NullInterchangeContext::temper_statics::instance.value));
}

template <class T>
static void roundTrip(std::shared_ptr<temper_std::JsonAdapter<T>> const & adapter, T value) {
  core::Str text = json_adapters::encode<T>(adapter, value);
  T back;
  try {
    back = json_adapters::decode<T>(adapter, text);
  } catch (core::TemperBubble const &) {
    core::panic();
  }
  core::Str again = json_adapters::encode<T>(adapter, back);
  core::Console::log(temper_globals::console.value, again == text ? text : core::Str(text + " became " + again));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_std::global_init_json();
    temper_globals::console.value = core::Console::get_console();
    json_adapters::roundTrip<std::shared_ptr<Point>>(Point::jsonAdapter(), Point::make(1, -2));
    json_adapters::roundTrip<std::shared_ptr<Record>>(Record::jsonAdapter(), Record::make(TEMPER_STR("Ada"), true, 1.5, int64_t{123456789012}, nullptr, core::List::make<std::shared_ptr<Point>>(Point::make(3, 4))));
    std::shared_ptr<std::vector<std::shared_ptr<Shape>>> shapes = core::List::make<std::shared_ptr<Shape>>(core::to_base<Shape>(Circle::make(2)), core::to_base<Shape>(Rect::make(3, 4)));
    json_adapters::roundTrip<std::shared_ptr<std::vector<std::shared_ptr<Shape>>>>(temper_std::listJsonAdapter<std::shared_ptr<Shape>>(Shape::jsonAdapter()), shapes);
    std::shared_ptr<std::vector<std::shared_ptr<Message>>> messages = core::List::make<std::shared_ptr<Message>>(core::to_base<Message>(Hello::make(TEMPER_STR("hi"))), core::to_base<Message>(Bye::make(TEMPER_STR("so long"))));
    json_adapters::roundTrip<std::shared_ptr<std::vector<std::shared_ptr<Message>>>>(temper_std::listJsonAdapter<std::shared_ptr<Message>>(Message::jsonAdapter()), messages);
    json_adapters::roundTrip<std::shared_ptr<Celsius>>(Celsius::jsonAdapter(), Celsius::make(21));
    std::shared_ptr<Shape> shape;
    try {
      shape = json_adapters::decode<std::shared_ptr<Shape>>(Shape::jsonAdapter(), TEMPER_STR("{\"height\": 6, \"width\": 5}"));
    } catch (core::TemperBubble const &) {
      core::panic();
    }
    {
      if (dynamic_cast<Rect const *>(shape.get()) != nullptr) {
        core::Console::log(temper_globals::console.value, "rect " + std::to_string(core::checked_cast<Rect>(shape)->width) + "x" + std::to_string(core::checked_cast<Rect>(shape)->height));
      } else {
        core::Console::log(temper_globals::console.value, TEMPER_STR("not a rect"));
      }
    }
    std::shared_ptr<Record> record;
    try {
      record = json_adapters::decode<std::shared_ptr<Record>>(Record::jsonAdapter(), TEMPER_STR("{\"name\": \"Bo\", \"is-active\": false, \"score\": 2, \"big\": 7, \"nickname\": \"B\", \"points\": []}"));
    } catch (core::TemperBubble const &) {
      core::panic();
    }
    core::Str string = record->name + " " + core::Boolean::toString(record->active) + " ";
    core::NullableParam<core::Str> subject = record->nickname;
    core::Console::log(temper_globals::console.value, string + (subject == nullptr ? TEMPER_STR("none") : subject.value) + " " + std::to_string(core::List::length<std::shared_ptr<Point>>(record->points)));
    core::Str value2;
    try {
      json_adapters::decode<std::shared_ptr<Point>>(Point::jsonAdapter(), TEMPER_STR("{\"x\": 1}"));
      value2 = TEMPER_STR("decoded");
    } catch (core::TemperBubble const &) {
      value2 = TEMPER_STR("no y");
    }
    core::Console::log(temper_globals::console.value, value2);
    core::Str value3;
    try {
      json_adapters::decode<std::shared_ptr<Message>>(Message::jsonAdapter(), TEMPER_STR("{\"kind\": \"what\", \"text\": \"?\"}"));
      value3 = TEMPER_STR("decoded");
    } catch (core::TemperBubble const &) {
      value3 = TEMPER_STR("no such kind");
    }
    core::Console::log(temper_globals::console.value, value3);
  });
}

}  // namespace json_adapters
