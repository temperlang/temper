// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <typeindex>

namespace classes {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Shape;
struct Rect;
struct Square;
struct Counter;
struct Point;
struct Loose;
template <class T>
struct Boxed;
struct Holder;
struct Version;
struct Stock;
struct Theme;
struct Span;
struct Sql;
struct Limited;
struct Stepper;
template <class T>
struct Failing;
struct IntFailing;
template <class T>
struct AnyFailing;

struct Shape : public virtual temper::core::AnyValueBase {
  virtual int32_t get_area() const = 0;
  virtual temper::core::Str describe() const;
};

struct Rect : public virtual Shape {
  struct temper_statics {
    static temper::core::GlobalSlot<std::shared_ptr<Rect>> unit;
  };
  int32_t w;
  int32_t h;
  static std::shared_ptr<Rect> const & unit();
  static std::shared_ptr<Rect> make(int32_t w, int32_t h);
  int32_t get_area() const override;
  temper::core::Str toString() const;
  std::shared_ptr<Rect> scaled(int32_t k) const;
  int32_t get_w() const;
  int32_t get_h() const;
};

struct Square : public virtual Shape {
  int32_t side;
  static std::shared_ptr<Square> make(int32_t side);
  int32_t get_area() const override;
  temper::core::Str describe() const override;
};

struct Counter : public std::enable_shared_from_this<Counter> {
  int32_t count;
  int32_t hidden;
  static std::shared_ptr<Counter> make();
  int32_t get_doubled();
  void set_doubled(int32_t value);
  void set_added(int32_t value);
  std::shared_ptr<Counter> bump();
  bool same(std::shared_ptr<Counter> const & other) const;
  static std::shared_ptr<Counter> zero();
  int32_t get_count();
};

struct Point : public std::enable_shared_from_this<Point> {
  int32_t x;
  int32_t y;
  static std::shared_ptr<Point> make(int32_t x, int32_t y);
  bool eq(std::shared_ptr<Point> const & other) const;
  int32_t get_x() const;
  int32_t get_y() const;
};

struct Loose : public std::enable_shared_from_this<Loose> {
  int32_t n;
  static std::shared_ptr<Loose> make(int32_t n);
  bool eq(std::shared_ptr<Loose> const & other) const;
  bool mirrors(std::shared_ptr<Loose> const & other);
  int32_t get_n() const;
};

template <class T>
struct Boxed : public std::enable_shared_from_this<Boxed<T>> {
  T value;
  int32_t tag;
  static std::shared_ptr<Boxed<T>> make(T value, int32_t tag);
  bool eq(std::shared_ptr<Boxed<T>> const & other) const;
  bool sameAs(std::shared_ptr<Boxed<T>> const & other);
  T get_value() const;
  int32_t get_tag() const;
};

struct Holder : public std::enable_shared_from_this<Holder> {
  std::shared_ptr<Loose> loose;
  temper::core::Str tag;
  static std::shared_ptr<Holder> make(std::shared_ptr<Loose> const & loose, temper::core::Str const & tag);
  bool eq(std::shared_ptr<Holder> const & other) const;
  std::shared_ptr<Loose> get_loose() const;
  temper::core::Str get_tag() const;
};

struct Version : public std::enable_shared_from_this<Version> {
  int32_t major;
  int32_t minor;
  static std::shared_ptr<Version> make(int32_t major, int32_t minor);
  int32_t compareTo(std::shared_ptr<Version> const & other) const;
  int32_t get_major() const;
  int32_t get_minor() const;
};

struct Stock : public std::enable_shared_from_this<Stock> {
  int32_t count;
  std::shared_ptr<std::vector<int32_t>> itemsVar;
  int32_t size;
  std::shared_ptr<std::vector<int32_t>> get_items();
  void set_items(std::shared_ptr<std::vector<int32_t>> const & value);
  static std::shared_ptr<Stock> make(std::shared_ptr<std::vector<int32_t>> const & items, temper::core::NullableParam<int32_t> count);
  static std::shared_ptr<Stock> make(std::shared_ptr<std::vector<int32_t>> const & items);
  int32_t sizeNow();
  int32_t get_count();
  int32_t get_size() const;
};

struct Theme : public std::enable_shared_from_this<Theme> {
  temper::core::Str fg;
  temper::core::Str bg;
  int32_t size;
  bool bold;
  static std::shared_ptr<Theme> make(temper::core::Str const & fg, temper::core::Str const & bg, temper::core::NullableParam<int32_t> size, temper::core::NullableParam<bool> bold);
  static std::shared_ptr<Theme> make(temper::core::Str const & fg, temper::core::Str const & bg, temper::core::NullableParam<int32_t> size);
  static std::shared_ptr<Theme> make(temper::core::Str const & fg, temper::core::Str const & bg);
  temper::core::Str get_weight() const;
  temper::core::Str toString() const;
  temper::core::Str get_fg() const;
  temper::core::Str get_bg() const;
  int32_t get_size() const;
  bool get_bold() const;
};

struct Span : public std::enable_shared_from_this<Span> {
  int32_t start;
  int32_t end;
  int32_t length;
  static std::shared_ptr<Span> make(int32_t start, int32_t end);
  int32_t get_start() const;
  int32_t get_end() const;
  int32_t get_length() const;
};

struct Sql : public std::enable_shared_from_this<Sql> {
  std::shared_ptr<std::vector<temper::core::Str>> parts;
  static std::shared_ptr<Sql> make();
  void appendSafe(temper::core::Str const & s) const;
  void appendString(temper::core::NullableParam<temper::core::Str> x) const;
  void appendInt(int32_t x) const;
  void appendShape(std::shared_ptr<Shape> const & x) const;
  void appendRect(std::shared_ptr<Rect> const & x) const;
  temper::core::Str get_accumulated() const;
};

struct Limited : public virtual temper::core::AnyValueBase {
  virtual int32_t next(int32_t n) const = 0;
  virtual int32_t tooFar() const = 0;
};

struct Stepper : public virtual Limited {
  int32_t limit;
  static std::shared_ptr<Stepper> make(int32_t limit);
  int32_t next(int32_t n) const override;
  int32_t tooFar() const override;
  int32_t get_limit() const;
};

template <class T>
struct Failing : public virtual temper::core::AnyValueBase {
  virtual T fail() const = 0;
};

struct IntFailing : public virtual Failing<int32_t> {
  static std::shared_ptr<IntFailing> make();
  int32_t fail() const override;
};

template <class T>
struct AnyFailing : public virtual Failing<T> {
  static std::shared_ptr<AnyFailing<T>> make();
  T fail() const override;
};

template <class T>
std::shared_ptr<Boxed<T>> Boxed<T>::make(T value, int32_t tag) {
  std::shared_ptr<Boxed<T>> self = std::make_shared<Boxed<T>>();
  self->value = value;
  self->tag = tag;
  return self;
}

template <class T>
bool Boxed<T>::eq(std::shared_ptr<Boxed<T>> const & other) const {
  return this->tag == other->tag;
}

template <class T>
bool Boxed<T>::sameAs(std::shared_ptr<Boxed<T>> const & other) {
  std::shared_ptr<Boxed<T>> self = this->shared_from_this();
  if (self->eq(other)) {
    return other->eq(self);
  } else {
    return false;
  }
}

template <class T>
T Boxed<T>::get_value() const {
  return this->value;
}

template <class T>
int32_t Boxed<T>::get_tag() const {
  return this->tag;
}

template <class T>
std::shared_ptr<AnyFailing<T>> AnyFailing<T>::make() {
  std::shared_ptr<AnyFailing<T>> self = std::make_shared<AnyFailing<T>>();
  return self;
}

template <class T>
T AnyFailing<T>::fail() const {
  temper::core::panic();
}

}  // namespace classes

// init.cpp
#include <classes/init.hpp>

namespace classes {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

core::Str Shape::describe() const {
  std::shared_ptr<Shape const> self = std::shared_ptr<Shape const>(shared_from_this(), this);
  return "shape of area " + std::to_string(self->get_area());
}

core::GlobalSlot<std::shared_ptr<Rect>> Rect::temper_statics::unit;

std::shared_ptr<Rect> const & Rect::unit() {
  global_init_init();
  return Rect::temper_statics::unit.value;
}

std::shared_ptr<Rect> Rect::make(int32_t w, int32_t h) {
  global_init_init();
  std::shared_ptr<Rect> self = std::make_shared<Rect>();
  self->w = w;
  self->h = h;
  return self;
}

int32_t Rect::get_area() const {
  return core::Int::mul(this->w, this->h);
}

core::Str Rect::toString() const {
  return "Rect(" + std::to_string(this->w) + ", " + std::to_string(this->h) + ")";
}

std::shared_ptr<Rect> Rect::scaled(int32_t k) const {
  std::shared_ptr<Rect const> self = std::shared_ptr<Rect const>(shared_from_this(), this);
  return Rect::make(core::Int::mul(self->w, k), core::Int::mul(self->h, k));
}

int32_t Rect::get_w() const {
  return this->w;
}

int32_t Rect::get_h() const {
  return this->h;
}

std::shared_ptr<Square> Square::make(int32_t side) {
  global_init_init();
  std::shared_ptr<Square> self = std::make_shared<Square>();
  self->side = side;
  return self;
}

int32_t Square::get_area() const {
  return core::Int::mul(this->side, this->side);
}

core::Str Square::describe() const {
  return "square of side " + std::to_string(this->side);
}

std::shared_ptr<Counter> Counter::make() {
  std::shared_ptr<Counter> self = std::make_shared<Counter>();
  self->count = 0;
  self->hidden = 0;
  return self;
}

int32_t Counter::get_doubled() {
  return core::Int::mul(this->count, 2);
}

void Counter::set_doubled(int32_t value) {
  this->count = core::Int::sub(value, this->count);
}

void Counter::set_added(int32_t value) {
  this->count = core::Int::add(this->count, value);
}

std::shared_ptr<Counter> Counter::bump() {
  std::shared_ptr<Counter> self = shared_from_this();
  self->count = core::Int::add(self->count, 1);
  self->hidden = core::Int::add(self->hidden, 1);
  return self;
}

bool Counter::same(std::shared_ptr<Counter> const & other) const {
  return this->hidden == other->hidden;
}

std::shared_ptr<Counter> Counter::zero() {
  return Counter::make();
}

int32_t Counter::get_count() {
  return this->count;
}

std::shared_ptr<Point> Point::make(int32_t x, int32_t y) {
  std::shared_ptr<Point> self = std::make_shared<Point>();
  self->x = x;
  self->y = y;
  return self;
}

bool Point::eq(std::shared_ptr<Point> const & other) const {
  if (this->x == other->x) {
    return this->y == other->y;
  } else {
    return false;
  }
}

int32_t Point::get_x() const {
  return this->x;
}

int32_t Point::get_y() const {
  return this->y;
}

std::shared_ptr<Loose> Loose::make(int32_t n) {
  std::shared_ptr<Loose> self = std::make_shared<Loose>();
  self->n = n;
  return self;
}

bool Loose::eq(std::shared_ptr<Loose> const & other) const {
  if (this->n == other->n) {
    return true;
  } else {
    return this->n == core::Int::neg(other->n);
  }
}

bool Loose::mirrors(std::shared_ptr<Loose> const & other) {
  std::shared_ptr<Loose> self = shared_from_this();
  if (other->eq(self)) {
    return self->eq(other);
  } else {
    return false;
  }
}

int32_t Loose::get_n() const {
  return this->n;
}

static std::shared_ptr<Loose> maybeLoose(int32_t n) {
  if (n == 0) {
    return nullptr;
  } else {
    return Loose::make(n);
  }
}

std::shared_ptr<Holder> Holder::make(std::shared_ptr<Loose> const & loose, core::Str const & tag) {
  std::shared_ptr<Holder> self = std::make_shared<Holder>();
  self->loose = loose;
  self->tag = tag;
  return self;
}

bool Holder::eq(std::shared_ptr<Holder> const & other) const {
  std::shared_ptr<Holder const> self = shared_from_this();
  if (self->loose->eq(other->loose)) {
    return self->tag == other->tag;
  } else {
    return false;
  }
}

std::shared_ptr<Loose> Holder::get_loose() const {
  return this->loose;
}

core::Str Holder::get_tag() const {
  return this->tag;
}

std::shared_ptr<Version> Version::make(int32_t major, int32_t minor) {
  std::shared_ptr<Version> self = std::make_shared<Version>();
  self->major = major;
  self->minor = minor;
  return self;
}

int32_t Version::compareTo(std::shared_ptr<Version> const & other) const {
  if (this->major != other->major) {
    return core::Compare::cmp(this->major, other->major);
  } else {
    return core::Compare::cmp(this->minor, other->minor);
  }
}

int32_t Version::get_major() const {
  return this->major;
}

int32_t Version::get_minor() const {
  return this->minor;
}

std::shared_ptr<std::vector<int32_t>> Stock::get_items() {
  return this->itemsVar;
}

void Stock::set_items(std::shared_ptr<std::vector<int32_t>> const & value) {
  std::shared_ptr<Stock> self = shared_from_this();
  self->itemsVar = value;
}

std::shared_ptr<Stock> Stock::make(std::shared_ptr<std::vector<int32_t>> const & items, core::NullableParam<int32_t> count) {
  std::shared_ptr<Stock> self = std::make_shared<Stock>();
  int32_t count2 = count == nullptr ? core::List::length<int32_t>(items) : count.value;
  self->count = count2;
  self->itemsVar = items;
  self->size = core::List::length<int32_t>(items);
  return self;
}

std::shared_ptr<Stock> Stock::make(std::shared_ptr<std::vector<int32_t>> const & items) {
  return make(items, nullptr);
}

int32_t Stock::sizeNow() {
  std::shared_ptr<Stock> self = shared_from_this();
  return core::List::length<int32_t>(self->get_items());
}

int32_t Stock::get_count() {
  return this->count;
}

int32_t Stock::get_size() const {
  return this->size;
}

static std::shared_ptr<Shape> shape(std::shared_ptr<Shape> const & x) {
  return x;
}

std::shared_ptr<Theme> Theme::make(core::Str const & fg, core::Str const & bg, core::NullableParam<int32_t> size, core::NullableParam<bool> bold) {
  std::shared_ptr<Theme> self = std::make_shared<Theme>();
  int32_t size2 = size == nullptr ? 12 : size.value;
  bool bold2 = bold == nullptr ? false : bold.value;
  self->fg = fg;
  self->bg = bg;
  self->size = size2;
  self->bold = bold2;
  return self;
}

std::shared_ptr<Theme> Theme::make(core::Str const & fg, core::Str const & bg, core::NullableParam<int32_t> size) {
  return make(fg, bg, size, nullptr);
}

std::shared_ptr<Theme> Theme::make(core::Str const & fg, core::Str const & bg) {
  return make(fg, bg, nullptr, nullptr);
}

core::Str Theme::get_weight() const {
  if (this->bold) {
    return TEMPER_STR("bold");
  } else {
    return TEMPER_STR("plain");
  }
}

core::Str Theme::toString() const {
  std::shared_ptr<Theme const> self = shared_from_this();
  return self->fg + "/" + self->bg + " " + std::to_string(self->size) + " " + self->get_weight();
}

core::Str Theme::get_fg() const {
  return this->fg;
}

core::Str Theme::get_bg() const {
  return this->bg;
}

int32_t Theme::get_size() const {
  return this->size;
}

bool Theme::get_bold() const {
  return this->bold;
}

static std::shared_ptr<Theme> picked(std::shared_ptr<Theme> const & t, core::Str const & why) {
  core::Console::log(temper_globals::console.value, why);
  return t;
}

static core::Str noted(core::Str const & s) {
  core::Console::log(temper_globals::console.value, s);
  return s;
}

std::shared_ptr<Span> Span::make(int32_t start, int32_t end) {
  std::shared_ptr<Span> self = std::make_shared<Span>();
  self->start = start;
  self->end = end;
  self->length = core::Int::sub(self->end, self->start);
  return self;
}

int32_t Span::get_start() const {
  return this->start;
}

int32_t Span::get_end() const {
  return this->end;
}

int32_t Span::get_length() const {
  return this->length;
}

static core::Str compared(std::function<bool(std::shared_ptr<Loose>, std::shared_ptr<Loose>)> const & eq) {
  std::shared_ptr<std::vector<int32_t>> ns = core::List::make<int32_t>(0, 1, -1, 2);
  std::shared_ptr<std::vector<core::Str>> out = core::ListBuilder::make<core::Str>();
  {
    int32_t length = core::List::length<int32_t>(ns);
    int32_t i = 0;
    while (i < length) {
      int32_t a = core::List::get<int32_t>(ns, i);
      i = core::Int::add(i, 1);
      {
        int32_t length2 = core::List::length<int32_t>(ns);
        int32_t i2 = 0;
        while (i2 < length2) {
          int32_t b = core::List::get<int32_t>(ns, i2);
          i2 = core::Int::add(i2, 1);
          core::ListBuilder::add<core::Str>(out, eq(maybeLoose(a), maybeLoose(b)) ? TEMPER_STR("T") : TEMPER_STR("f"));
        }
      }
    }
  }
  return core::List::join(out, core::Str(), [](core::Str s) -> core::Str { return s; });
}

static std::type_index typeNamed(int32_t n) {
  {
    if (n == 0) {
      return std::type_index(typeid(void));
    } else if (n == 1) {
      return std::type_index(typeid(bool));
    } else if (n == 2) {
      return std::type_index(typeid(int32_t));
    } else if (n == 3) {
      return std::type_index(typeid(int64_t));
    } else if (n == 4) {
      return std::type_index(typeid(double));
    } else {
      return std::type_index(typeid(core::Str));
    }
  }
}

static std::type_index same(std::type_index t) {
  return t;
}

std::shared_ptr<Sql> Sql::make() {
  std::shared_ptr<Sql> self = std::make_shared<Sql>();
  self->parts = core::ListBuilder::make<core::Str>();
  return self;
}

void Sql::appendSafe(core::Str const & s) const {
  core::ListBuilder::add<core::Str>(this->parts, s);
}

void Sql::appendString(core::NullableParam<core::Str> x) const {
  core::ListBuilder::add<core::Str>(this->parts, x == nullptr ? TEMPER_STR("NULL") : core::Str("'" + x.value + "'"));
}

void Sql::appendInt(int32_t x) const {
  core::ListBuilder::add<core::Str>(this->parts, std::to_string(x));
}

void Sql::appendShape(std::shared_ptr<Shape> const & x) const {
  std::shared_ptr<Sql const> self = shared_from_this();
  core::ListBuilder::add<core::Str>(self->parts, "shape " + std::to_string(x->get_area()));
}

void Sql::appendRect(std::shared_ptr<Rect> const & x) const {
  std::shared_ptr<Sql const> self = shared_from_this();
  core::ListBuilder::add<core::Str>(self->parts, "rect " + x->toString());
}

core::Str Sql::get_accumulated() const {
  return core::List::join(this->parts, core::Str(), [](core::Str p) -> core::Str { return p; });
}

std::shared_ptr<Stepper> Stepper::make(int32_t limit) {
  global_init_init();
  std::shared_ptr<Stepper> self = std::make_shared<Stepper>();
  self->limit = limit;
  return self;
}

int32_t Stepper::next(int32_t n) const {
  std::shared_ptr<Stepper const> self = std::shared_ptr<Stepper const>(shared_from_this(), this);
  if (n < self->limit) {
    return core::Int::add(n, 1);
  } else {
    return self->tooFar();
  }
}

int32_t Stepper::tooFar() const {
  core::panic();
}

int32_t Stepper::get_limit() const {
  return this->limit;
}

std::shared_ptr<IntFailing> IntFailing::make() {
  std::shared_ptr<IntFailing> self = std::make_shared<IntFailing>();
  return self;
}

int32_t IntFailing::fail() const {
  core::panic();
}

template <class T>
static T orFail(bool ok, T x, std::shared_ptr<Failing<T>> const & f) {
  if (ok) {
    return x;
  } else {
    return f->fail();
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    Rect::temper_statics::unit.construct();
    temper_globals::console.value = core::Console::get_console();
    Rect::temper_statics::unit.value = Rect::make(1, 1);
    std::shared_ptr<Rect> r = Rect::make(2, 3);
    core::Console::log(temper_globals::console.value, r->toString());
    core::Console::log(temper_globals::console.value, std::to_string(r->get_area()));
    std::shared_ptr<Shape> s = classes::shape(core::to_base<Shape>(r));
    core::Console::log(temper_globals::console.value, s->describe());
    std::shared_ptr<Shape> q = classes::shape(core::to_base<Shape>(Square::make(4)));
    core::Console::log(temper_globals::console.value, q->describe());
    core::Console::log(temper_globals::console.value, std::to_string(q->get_area()));
    core::Console::log(temper_globals::console.value, std::to_string(r->scaled(2)->scaled(3)->get_area()));
    core::Console::log(temper_globals::console.value, Rect::temper_statics::unit.value->scaled(5)->toString());
    std::shared_ptr<Counter> c = Counter::zero();
    c->bump()->bump();
    core::Console::log(temper_globals::console.value, std::to_string(c->count));
    c->set_doubled(10);
    core::Console::log(temper_globals::console.value, std::to_string(c->get_doubled()));
    core::Console::log(temper_globals::console.value, core::Boolean::toString(c->same(Counter::make()->bump()->bump())));
    c->set_added(5);
    int32_t t = 2;
    c->set_added(t);
    int32_t t2 = t;
    c->set_added(t2);
    int32_t added = t2;
    core::Console::log(temper_globals::console.value, std::to_string(added) + " " + std::to_string(c->count));
    core::Console::log(temper_globals::console.value, core::Boolean::toString(Point::make(1, 2)->eq(Point::make(1, 2))) + " " + core::Boolean::toString(!Point::make(1, 2)->eq(Point::make(2, 1))));
    core::Console::log(temper_globals::console.value, core::Boolean::toString(Holder::make(Loose::make(3), TEMPER_STR("a"))->eq(Holder::make(Loose::make(-3), TEMPER_STR("a")))));
    std::shared_ptr<Loose> loose = Loose::make(2);
    core::Str string = core::Boolean::toString(loose->mirrors(Loose::make(-2))) + " ";
    std::shared_ptr<Loose> left = maybeLoose(-2);
    std::shared_ptr<Loose> left2 = loose;
    std::shared_ptr<Loose> right = maybeLoose(0);
    std::shared_ptr<Loose> left3 = maybeLoose(0);
    core::Console::log(temper_globals::console.value, string + core::Boolean::toString(left == nullptr ? false : left->eq(loose)) + " " + core::Boolean::toString(right == nullptr ? false : left2->eq(right)) + " " + core::Boolean::toString(!(left3 == nullptr ? false : left3->eq(loose))));
    std::shared_ptr<Loose> subject = maybeLoose(-2);
    core::Str matched = (subject == nullptr ? false : subject->eq(loose)) ? TEMPER_STR("loose") : TEMPER_STR("other");
    core::Console::log(temper_globals::console.value, matched);
    std::shared_ptr<Boxed<core::Str>> b1 = Boxed<core::Str>::make(TEMPER_STR("x"), 1);
    std::shared_ptr<Boxed<core::Str>> b3 = Boxed<core::Str>::make(TEMPER_STR("z"), 2);
    core::Console::log(temper_globals::console.value, b1->value + " " + core::Boolean::toString(b1->eq(Boxed<core::Str>::make(TEMPER_STR("y"), 1))) + " " + core::Boolean::toString(!b1->eq(b3)) + " " + core::Boolean::toString(b1->sameAs(Boxed<core::Str>::make(TEMPER_STR("y"), 1))) + " " + core::Boolean::toString(b3->sameAs(b1)));
    core::Console::log(temper_globals::console.value, core::Boolean::toString(Version::make(1, 2)->compareTo(Version::make(1, 3)) < 0) + " " + core::Boolean::toString(Version::make(2, 0)->compareTo(Version::make(1, 9)) >= 0));
    core::Console::log(temper_globals::console.value, core::Boolean::toString(dynamic_cast<Square const *>(q.get()) != nullptr) + " " + core::Boolean::toString(dynamic_cast<Square const *>(s.get()) != nullptr));
    std::shared_ptr<Square> sq = core::checked_cast<Square>(q);
    core::Console::log(temper_globals::console.value, std::to_string(sq->get_area()));
    if (dynamic_cast<Rect const *>(s.get()) != nullptr) {
      core::Console::log(temper_globals::console.value, std::to_string(core::checked_cast<Rect>(s)->w));
    }
    std::shared_ptr<Stock> st = Stock::make(core::List::make<int32_t>(4, 5));
    st->set_items(core::List::make<int32_t>(1, 2, 3));
    core::Console::log(temper_globals::console.value, std::to_string(st->count) + " " + std::to_string(st->size) + " " + std::to_string(st->sizeNow()) + " " + std::to_string(core::List::length<int32_t>(st->get_items())));
    std::shared_ptr<Theme> plain = Theme::make(TEMPER_STR("black"), TEMPER_STR("white"));
    std::shared_ptr<Theme> spread = plain;
    std::shared_ptr<Theme> loud = Theme::make(spread->fg, spread->bg, 20, true);
    std::shared_ptr<Theme> spread2 = loud;
    std::shared_ptr<Theme> dark = Theme::make(plain->bg, plain->fg, spread2->size, spread2->bold);
    core::Console::log(temper_globals::console.value, plain->toString() + " | " + loud->toString() + " | " + dark->toString());
    std::shared_ptr<Theme> spread3 = classes::picked(dark, TEMPER_STR("old"));
    core::Str arg = spread3->bg;
    core::Str arg2 = classes::noted(TEMPER_STR("red"));
    bool arg3 = spread3->bold;
    std::shared_ptr<Theme> mixed = Theme::make(arg2, arg, 9, arg3);
    core::Console::log(temper_globals::console.value, mixed->toString());
    std::shared_ptr<Theme> spread4 = classes::picked(plain, TEMPER_STR("first"));
    core::Str value2 = classes::noted(TEMPER_STR("second"));
    std::shared_ptr<Theme> ordered = Theme::make(value2, classes::noted(TEMPER_STR("third")), spread4->size, spread4->bold);
    core::Console::log(temper_globals::console.value, ordered->toString());
    std::shared_ptr<Span> spread5 = Span::make(2, 5);
    std::shared_ptr<Span> wide = Span::make(spread5->start, 9);
    core::Console::log(temper_globals::console.value, std::to_string(wide->start) + " " + std::to_string(wide->end) + " " + std::to_string(wide->length));
    core::Console::log(temper_globals::console.value, classes::compared([](std::shared_ptr<Loose> a, std::shared_ptr<Loose> b) -> bool { return a == nullptr ? b == nullptr : b == nullptr ? false : a->eq(b); }));
    core::Console::log(temper_globals::console.value, classes::compared([](std::shared_ptr<Loose> a, std::shared_ptr<Loose> b) -> bool { return !(a == nullptr ? b == nullptr : b == nullptr ? false : a->eq(b)); }));
    core::Console::log(temper_globals::console.value, classes::compared([](std::shared_ptr<Loose> a, std::shared_ptr<Loose> b) -> bool { return b == nullptr ? a == nullptr : a == nullptr ? false : b->eq(a); }));
    auto fn4 = [](std::shared_ptr<Loose> a, std::shared_ptr<Loose> b) -> bool {
      if (a == nullptr) {
        return false;
      }
      if (b == nullptr) {
        return false;
      }
      return a->mirrors(b);
    };
    core::Console::log(temper_globals::console.value, classes::compared(fn4));
    std::type_index favorite = classes::same(std::type_index(typeid(int64_t)));
    static_cast<void>(favorite);
    int32_t named = 0;
    {
      int32_t i = 0;
      while (i < 6) {
        typeNamed(i);
        named = core::Int::add(named, 1);
        i = core::Int::add(i, 1);
      }
    }
    core::Console::log(temper_globals::console.value, std::to_string(named) + " types");
    std::shared_ptr<Sql> sql = Sql::make();
    sql->appendString(TEMPER_STR("it"));
    sql->appendString(nullptr);
    sql->appendInt(3);
    sql->appendShape(core::to_base<Shape>(Square::make(2)));
    sql->appendRect(Rect::make(1, 2));
    core::Console::log(temper_globals::console.value, sql->get_accumulated());
    int32_t seven = 7;
    std::shared_ptr<Sql> accumulator = Sql::make();
    {
      accumulator->appendSafe(TEMPER_STR("SELECT "));
      accumulator->appendInt(seven);
      accumulator->appendSafe(TEMPER_STR(", "));
      accumulator->appendString(TEMPER_STR("x"));
      accumulator->appendSafe(TEMPER_STR(" FROM "));
      accumulator->appendRect(Rect::make(2, 2));
    }
    core::Console::log(temper_globals::console.value, accumulator->get_accumulated());
    std::shared_ptr<Limited> stepper = core::to_base<Limited>(Stepper::make(3));
    core::Console::log(temper_globals::console.value, "next " + std::to_string(stepper->next(1)));
    int32_t int32 = classes::orFail<int32_t>(true, 4, core::to_base<Failing<int32_t>>(IntFailing::make()));
    core::Console::log(temper_globals::console.value, std::to_string(int32) + " " + classes::orFail<core::Str>(true, TEMPER_STR("s"), core::to_base<Failing<core::Str>>(AnyFailing<core::Str>::make())));
  });
}

}  // namespace classes
