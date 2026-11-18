// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace libraries {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Grid;
struct Casing;
struct Shape;
struct Dot;
struct Line;
struct Carry;
struct Tagged;
struct Word;
struct Joiner;
struct Shown;
struct Tag;
template <class T>
struct Held;
template <class T>
struct Effect;
template <class T>
struct Plain;
template <class T>
struct Happened;
template <class T>
struct Bundle;
template <class T>
struct Stepper;
template <class T>
struct Counting;
struct Ticks;
template <class T>
struct Sink;
struct Names;
struct Escapes;
struct Quoting;

struct Grid : public std::enable_shared_from_this<Grid> {
  int32_t ncols;
  std::shared_ptr<std::vector<double>> values;
  static std::shared_ptr<Grid> make(int32_t ncols, std::shared_ptr<std::vector<double>> const & values);
  double get(int32_t i, int32_t j) const;
  static std::shared_ptr<Grid> parse(temper::core::Str const & s);
  int32_t get_ncols() const;
  std::shared_ptr<std::vector<double>> get_values() const;
};

struct Casing : public std::enable_shared_from_this<Casing> {
  struct temper_statics {
    static temper::core::GlobalSlot<std::shared_ptr<Casing>> first;
    static temper::core::GlobalSlot<std::shared_ptr<Casing>> last;
    static temper::core::GlobalSlot<temper::core::Str> mark;
  };
  temper::core::Str name;
  static std::shared_ptr<Casing> const & first();
  static std::shared_ptr<Casing> const & last();
  bool eq(std::shared_ptr<Casing> const & other) const;
  static std::shared_ptr<Casing> make(temper::core::Str const & name);
  temper::core::Str toString() const;
  static std::shared_ptr<Casing> named(temper::core::Str const & name);
  temper::core::Str get_marked() const;
  temper::core::Str get_name() const;
};

struct Shape : public virtual temper::core::AnyValueBase {
  virtual int32_t get_size() const = 0;
};

struct Dot : public virtual Shape {
  int32_t size;
  static std::shared_ptr<Dot> make(int32_t size);
  std::shared_ptr<Dot> get_bigger() const;
  temper::core::NullableParam<int32_t> get_grown() const;
  int32_t scaled(int32_t k) const;
  int32_t get_size() const override;
};

struct Line : public virtual Shape {
  int32_t size;
  static std::shared_ptr<Line> make(int32_t size);
  int32_t get_size() const override;
};

struct Carry : public std::enable_shared_from_this<Carry> {
  bool carrying;
  int32_t state;
  static std::shared_ptr<Carry> make(int32_t start);
  bool get_carrying() const;
  int32_t get_state() const;
};

struct Tagged : public std::enable_shared_from_this<Tagged> {
  temper::core::NullableParam<temper::core::Str> label;
  static std::shared_ptr<Tagged> make(temper::core::NullableParam<temper::core::Str> label);
  temper::core::NullableParam<temper::core::Str> get_label() const;
};

struct Word : public std::enable_shared_from_this<Word> {
  temper::core::Str text;
  static std::shared_ptr<Word> make(temper::core::Str const & text);
  temper::core::Str get_text() const;
};

struct Joiner : public std::enable_shared_from_this<Joiner> {
  temper::core::Str out;
  static std::shared_ptr<Joiner> make();
  void addWord(std::shared_ptr<Word> const & w);
  void addFlags(std::shared_ptr<std::vector<bool>> const & x);
  void addTexts(std::shared_ptr<std::vector<temper::core::Str>> const & x);
  void addMaybes(std::shared_ptr<std::vector<temper::core::NullableParam<temper::core::Str>>> const & x);
  temper::core::Str get_out();
};

struct Shown : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str toString() const = 0;
};

struct Tag : public virtual Shown {
  temper::core::Str name;
  static std::shared_ptr<Tag> make(temper::core::Str const & name);
  temper::core::Str toString() const override;
  temper::core::Str get_name() const;
};

template <class T>
struct Held : public std::enable_shared_from_this<Held<T>> {
  T item;
  std::shared_ptr<Held<T>> next;
  static std::shared_ptr<Held<T>> make(T item, std::shared_ptr<Held<T>> const & next);
  temper::core::Str toString() const;
  T get_item() const;
  std::shared_ptr<Held<T>> get_next() const;
};

template <class T>
struct Effect : public virtual temper::core::AnyValueBase {};

template <class T>
struct Plain : public virtual Effect<T> {
  temper::core::Str text;
  static std::shared_ptr<Plain<T>> make(temper::core::Str const & text);
  temper::core::Str get_text() const;
};

template <class T>
struct Happened : public virtual Effect<T> {
  T event;
  static std::shared_ptr<Happened<T>> make(T event);
  T get_event() const;
};

template <class T>
struct Bundle : public std::enable_shared_from_this<Bundle<T>> {
  std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> effects;
  T first;
  static std::shared_ptr<Bundle<T>> make(std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> const & effects, T first);
  std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> get_effects() const;
  T get_first() const;
};

template <class T>
struct Stepper : public virtual temper::core::AnyValueBase {
  virtual std::shared_ptr<std::vector<T>> step() = 0;
};

template <class T>
struct Counting : public virtual Stepper<T> {
  virtual int32_t get_count() = 0;
  virtual void set_count(int32_t c) = 0;
  virtual T made() const = 0;
  std::shared_ptr<std::vector<T>> step() override;
};

struct Ticks : public virtual Counting<temper::core::Str> {
  int32_t n;
  static std::shared_ptr<Ticks> make();
  int32_t get_count() override;
  void set_count(int32_t c) override;
  temper::core::Str made() const override;
};

template <class T>
struct Sink : public virtual temper::core::AnyValueBase {
  virtual int32_t take(T e) const = 0;
};

struct Names : public virtual Sink<std::shared_ptr<Tag>> {
  static std::shared_ptr<Names> make();
  int32_t take(std::shared_ptr<Tag> e) const override;
};

struct Escapes : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str escape(temper::core::NullableParam<temper::core::Str> s) const = 0;
  virtual temper::core::Str escapeFlag(bool b) const;
};

struct Quoting : public virtual Escapes {
  static std::shared_ptr<Quoting> make();
  temper::core::Str escape(temper::core::NullableParam<temper::core::Str> s) const override;
};

// Storage, which the library reads and its init sets; read values
// through the accessors below.
namespace temper_globals {
extern temper::core::GlobalSlot<std::shared_ptr<std::vector<int32_t>>> extras;
}  // namespace temper_globals

std::shared_ptr<Grid> parseGrid(temper::core::Str const & s);

template <class T>
int32_t sizeWith(std::shared_ptr<std::vector<T>> const & items) {
  global_init_init();
  return temper::core::Int::add(temper::core::List::length<T>(items), temper::core::List::length<int32_t>(temper_globals::extras.value));
}

template <class T>
std::shared_ptr<Held<T>> Held<T>::make(T item, std::shared_ptr<Held<T>> const & next) {
  global_init_init();
  std::shared_ptr<Held<T>> self = std::make_shared<Held<T>>();
  self->item = item;
  self->next = next;
  return self;
}

template <class T>
temper::core::Str Held<T>::toString() const {
  std::shared_ptr<Held<T> const> self = this->shared_from_this();
  return "Held(" + self->item->toString() + ")";
}

template <class T>
T Held<T>::get_item() const {
  return this->item;
}

template <class T>
std::shared_ptr<Held<T>> Held<T>::get_next() const {
  return this->next;
}

template <class T>
std::shared_ptr<Plain<T>> Plain<T>::make(temper::core::Str const & text) {
  global_init_init();
  std::shared_ptr<Plain<T>> self = std::make_shared<Plain<T>>();
  self->text = text;
  return self;
}

template <class T>
temper::core::Str Plain<T>::get_text() const {
  return this->text;
}

template <class T>
std::shared_ptr<Happened<T>> Happened<T>::make(T event) {
  global_init_init();
  std::shared_ptr<Happened<T>> self = std::make_shared<Happened<T>>();
  self->event = event;
  return self;
}

template <class T>
T Happened<T>::get_event() const {
  return this->event;
}

template <class T>
std::shared_ptr<Bundle<T>> Bundle<T>::make(std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> const & effects, T first) {
  global_init_init();
  std::shared_ptr<Bundle<T>> self = std::make_shared<Bundle<T>>();
  self->effects = effects;
  self->first = first;
  return self;
}

template <class T>
std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> Bundle<T>::get_effects() const {
  return this->effects;
}

template <class T>
T Bundle<T>::get_first() const {
  return this->first;
}

template <class T>
int32_t joinEffects(std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> const & into, std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> const & more) {
  {
    int32_t length = temper::core::List::length<std::shared_ptr<Effect<T>>>(more);
    int32_t i = 0;
    while (i < length) {
      std::shared_ptr<Effect<T>> e = temper::core::List::get<std::shared_ptr<Effect<T>>>(more, i);
      i = temper::core::Int::add(i, 1);
      temper::core::ListBuilder::add<std::shared_ptr<Effect<T>>>(into, e);
    }
  }
  return temper::core::List::length<std::shared_ptr<Effect<T>>>(into);
}

template <class T>
temper::core::Str effectsOf(temper::core::Str const & text, T event, T other) {
  global_init_init();
  std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> into = temper::core::ListBuilder::make<std::shared_ptr<Effect<T>>>();
  libraries::joinEffects<T>(into, temper::core::List::make<std::shared_ptr<Effect<T>>>());
  libraries::joinEffects<T>(into, temper::core::List::make<std::shared_ptr<Effect<T>>>(temper::core::to_base<Effect<T>>(Plain<T>::make(text))));
  libraries::joinEffects<T>(into, temper::core::List::make<std::shared_ptr<Effect<T>>>(temper::core::to_base<Effect<T>>(Happened<T>::make(event)), temper::core::to_base<Effect<T>>(Plain<T>::make(TEMPER_STR("!"))), temper::core::to_base<Effect<T>>(Happened<T>::make(other))));
  temper::core::Str shown = temper::core::Str();
  {
    std::shared_ptr<std::vector<std::shared_ptr<Effect<T>>>> list = temper::core::List::toList<std::shared_ptr<Effect<T>>>(into);
    int32_t length = temper::core::List::length<std::shared_ptr<Effect<T>>>(list);
    int32_t i = 0;
    while (i < length) {
      std::shared_ptr<Effect<T>> e = temper::core::List::get<std::shared_ptr<Effect<T>>>(list, i);
      i = temper::core::Int::add(i, 1);
      if (dynamic_cast<Plain<T> const *>(e.get()) != nullptr) {
        shown = shown + temper::core::checked_cast<Plain<T>>(e)->text;
      } else {
        shown = shown + "*";
      }
    }
  }
  return shown;
}

template <class T>
bool sameHeld(std::shared_ptr<Held<T>> const & a, std::shared_ptr<Held<T>> const & b) {
  global_init_init();
  std::shared_ptr<Held<T>> left = a;
  std::shared_ptr<Held<T>> right = b;
  return left == nullptr ? right == nullptr : right == nullptr ? false : left == right;
}

template <class T>
std::shared_ptr<std::vector<T>> Counting<T>::step() {
  std::shared_ptr<Counting<T>> self = std::shared_ptr<Counting<T>>(this->shared_from_this(), this);
  self->set_count(temper::core::Int::add(self->get_count(), 1));
  return temper::core::List::make<T>(self->made());
}

}  // namespace libraries

// init.cpp
#include <libraries/init.hpp>

#include <mutex>

namespace libraries {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<int32_t> ticks;
core::GlobalSlot<std::shared_ptr<std::vector<int32_t>>> extras;

}  // namespace temper_globals

static std::mutex moduleMutex;

// Reads a variable that functions write, under the module's lock.
template <class T>
static T load(T const &variable) {
  std::lock_guard<std::mutex> lock(moduleMutex);
  return variable;
}

std::shared_ptr<Grid> Grid::make(int32_t ncols, std::shared_ptr<std::vector<double>> const & values) {
  global_init_init();
  std::shared_ptr<Grid> self = std::make_shared<Grid>();
  self->ncols = ncols;
  self->values = values;
  return self;
}

double Grid::get(int32_t i, int32_t j) const {
  return core::List::get<double>(this->values, core::Int::add(core::Int::mul(i, this->ncols), j));
}

std::shared_ptr<Grid> Grid::parse(core::Str const & s) {
  global_init_init();
  try {
    return libraries::parseGrid(s);
  } catch (core::TemperBubble const &) {
    return nullptr;
  }
}

int32_t Grid::get_ncols() const {
  return this->ncols;
}

std::shared_ptr<std::vector<double>> Grid::get_values() const {
  return this->values;
}

std::shared_ptr<Grid> parseGrid(core::Str const & s) {
  global_init_init();
  if (s == core::Str()) {
    core::bubble();
  }
  return Grid::make(1, core::List::make<double>(core::String::toFloat64(s)));
}

core::GlobalSlot<std::shared_ptr<Casing>> Casing::temper_statics::first;

std::shared_ptr<Casing> const & Casing::first() {
  global_init_init();
  return Casing::temper_statics::first.value;
}

core::GlobalSlot<std::shared_ptr<Casing>> Casing::temper_statics::last;

std::shared_ptr<Casing> const & Casing::last() {
  global_init_init();
  return Casing::temper_statics::last.value;
}

core::GlobalSlot<core::Str> Casing::temper_statics::mark;

bool Casing::eq(std::shared_ptr<Casing> const & other) const {
  return this->name == other->name;
}

std::shared_ptr<Casing> Casing::make(core::Str const & name) {
  std::shared_ptr<Casing> self = std::make_shared<Casing>();
  self->name = name;
  return self;
}

core::Str Casing::toString() const {
  return this->name;
}

std::shared_ptr<Casing> Casing::named(core::Str const & name) {
  return Casing::make(name);
}

core::Str Casing::get_marked() const {
  return Casing::temper_statics::mark.value + this->name;
}

core::Str Casing::get_name() const {
  return this->name;
}

std::shared_ptr<Dot> Dot::make(int32_t size) {
  global_init_init();
  std::shared_ptr<Dot> self = std::make_shared<Dot>();
  self->size = size;
  return self;
}

std::shared_ptr<Dot> Dot::get_bigger() const {
  std::shared_ptr<Dot const> self = std::shared_ptr<Dot const>(shared_from_this(), this);
  if (self->size < 3) {
    return Dot::make(core::Int::add(self->size, 1));
  } else {
    return nullptr;
  }
}

core::NullableParam<int32_t> Dot::get_grown() const {
  std::shared_ptr<Dot const> self = std::shared_ptr<Dot const>(shared_from_this(), this);
  {
    std::shared_ptr<Dot> subject = self->get_bigger();
    if (subject == nullptr) {
      return nullptr;
    } else {
      return subject->scaled(10);
    }
  }
}

int32_t Dot::scaled(int32_t k) const {
  return core::Int::mul(this->size, k);
}

int32_t Dot::get_size() const {
  return this->size;
}

std::shared_ptr<Line> Line::make(int32_t size) {
  global_init_init();
  std::shared_ptr<Line> self = std::make_shared<Line>();
  self->size = size;
  return self;
}

int32_t Line::get_size() const {
  return this->size;
}

std::shared_ptr<Carry> Carry::make(int32_t start) {
  global_init_init();
  std::shared_ptr<Carry> self = std::make_shared<Carry>();
  self->carrying = false;
  self->state = 0;
  if (start != 0) {
    self->carrying = true;
    self->state = start;
  }
  return self;
}

bool Carry::get_carrying() const {
  return this->carrying;
}

int32_t Carry::get_state() const {
  return this->state;
}

std::shared_ptr<Tagged> Tagged::make(core::NullableParam<core::Str> label) {
  global_init_init();
  std::shared_ptr<Tagged> self = std::make_shared<Tagged>();
  self->label = label;
  return self;
}

core::NullableParam<core::Str> Tagged::get_label() const {
  return this->label;
}

static std::shared_ptr<Tagged> tagged(core::NullableParam<core::Str> label) {
  return Tagged::make(label);
}

static std::shared_ptr<Tagged> tagged() {
  return tagged(nullptr);
}

static int32_t weight(int32_t c) {
  int32_t base = c == 42 ? 0 : 6;
  return core::Int::add(core::Int::add(c > 50 ? 1 : 2, base), c);
}

static core::Str shapeName(std::shared_ptr<Shape> const & s) {
  core::Str name = dynamic_cast<Dot const *>(s.get()) != nullptr ? TEMPER_STR("dot") : dynamic_cast<Line const *>(s.get()) != nullptr ? TEMPER_STR("line") : core::panic<core::Str>();
  return name;
}

static std::shared_ptr<Shape> shapeOf(int32_t n) {
  std::shared_ptr<Shape> shape = n > 1 ? core::to_base<Shape>(Line::make(n)) : core::to_base<Shape>(Dot::make(n));
  return shape;
}

static std::shared_ptr<std::vector<int32_t>> sizes(std::shared_ptr<Shape> const & s) {
  std::shared_ptr<std::vector<int32_t>> found = dynamic_cast<Dot const *>(s.get()) != nullptr ? core::List::make<int32_t>(core::checked_cast<Dot>(s)->size, core::checked_cast<Dot>(s)->size) : core::List::make<int32_t>();
  return found;
}

static int32_t rows(std::shared_ptr<std::vector<double>> const & values, int32_t ncols) {
  try {
    return core::Int::div_wrap(core::List::length<double>(values), ncols);
  } catch (core::TemperBubble const &) {
    core::panic();
  }
}

static std::shared_ptr<Grid> scaled(std::shared_ptr<Grid> const & grid) {
  if (grid->ncols == 0) {
    core::bubble();
  }
  return grid;
}

static std::shared_ptr<std::vector<double>> cubes(std::shared_ptr<Grid> const & grid) {
  try {
    std::shared_ptr<std::vector<double>> more = core::List::map(libraries::scaled(grid)->values, [](double x) -> double { return x * x * x; });
    return more;
  } catch (core::TemperBubble const &) {
    core::panic();
  }
}

static void each(std::shared_ptr<std::vector<double>> const & values, std::function<void(double)> const & f) {
  {
    int32_t i = 0;
    while (i < core::List::length<double>(values)) {
      f(core::List::get<double>(values, i));
      i = core::Int::add(i, 1);
    }
  }
}

static double total(std::shared_ptr<std::vector<double>> const & values) {
  std::shared_ptr<double> sum = std::make_shared<double>(0.0);
  libraries::each(values, [=](double x) -> void { try { double part = x / 2.0; *sum = (*sum) + part; } catch (core::TemperBubble const &) { core::panic(); } });
  return (*sum);
}

static double half(double x) {
  try {
    return x / 2.0;
  } catch (core::TemperBubble const &) {
    core::panic();
  }
}

static double cube(double x) {
  return x * 0.25;
}

static double corner(std::shared_ptr<Grid> const & grid) {
  try {
    return grid->get(1, 1);
  } catch (core::TemperBubble const &) {
    return -1.0;
  }
}

static std::shared_ptr<Grid> maybe(int32_t n) {
  if (n > 0) {
    return Grid::make(n, core::List::make<double>(1.0));
  } else {
    return nullptr;
  }
}

static std::shared_ptr<std::vector<double>> mapped(std::shared_ptr<Grid> const & grid) {
  std::shared_ptr<Grid> value2;
  try {
    value2 = libraries::scaled(grid);
  } catch (core::TemperBubble const &) {
    core::panic();
  }
  return core::List::map(value2->values, [](double x) -> double { if (core::Float64::gt(x, 1.5)) { return x * 2.0; } else { return x; } });
}

static int32_t byteAt(std::shared_ptr<std::vector<int32_t>> const & digits, bool short_, int32_t c) {
  int32_t byte = short_ ? core::Int::mul(core::List::get<int32_t>(digits, c), 17) : core::Int::add(core::Int::mul(core::List::get<int32_t>(digits, core::Int::mul(2, c)), 16), core::List::get<int32_t>(digits, core::Int::add(core::Int::mul(2, c), 1)));
  return byte;
}

static bool ascending(std::shared_ptr<std::vector<int32_t>> const & digits) {
  if (core::List::length<int32_t>(digits) > 1) {
    return core::Int::add(core::List::get<int32_t>(digits, 0), 1) == core::List::get<int32_t>(digits, 1);
  } else {
    return false;
  }
}

static int32_t firstDrop(std::shared_ptr<std::vector<int32_t>> const & digits) {
  int32_t i = 1;
  while (i < core::List::length<int32_t>(digits) && core::List::get<int32_t>(digits, core::Int::sub(i, 1)) <= core::List::get<int32_t>(digits, i)) {
    i = core::Int::add(i, 1);
  }
  return i;
}

static int32_t tick() {
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::ticks.value = core::Int::add(temper_globals::ticks.value, 1);
  return temper_globals::ticks.value;
}

static int32_t tickIf(bool short_) {
  int32_t int32;
  if (short_) {
    int32 = tick();
  } else {
    int32_t value2 = core::Int::mul(tick(), 10);
    int32 = core::Int::add(value2, tick());
  }
  int32_t n = int32;
  return n;
}

static bool ticked() {
  if (load(temper_globals::ticks.value) > 0) {
    int32_t value2 = core::Int::mul(tick(), 10);
    return core::Int::add(value2, tick()) > 0;
  } else {
    return false;
  }
}

static int32_t tickWhile() {
  int32_t i = 0;
  while (true) {
    bool boolean = i < 3;
    if (boolean) {
      int32_t value2 = tick();
      boolean = value2 < tick();
    }
    if (!boolean) {
      break;
    }
    i = core::Int::add(i, 1);
  }
  return i;
}

static core::NullableParam<core::Str> text(int32_t n) {
  if (n > 0) {
    return TEMPER_STR("x");
  } else {
    return nullptr;
  }
}

static core::NullableParam<int32_t> count(int32_t n) {
  if (n > 0) {
    return n;
  } else {
    return nullptr;
  }
}

static std::shared_ptr<Casing> casing(int32_t n) {
  if (n > 0) {
    return Casing::temper_statics::first.value;
  } else {
    return nullptr;
  }
}

static core::Str captured(core::Str const & s) {
  std::shared_ptr<Grid> found;
  try {
    found = libraries::parseGrid(s);
  } catch (core::TemperBubble const &) {
    found = nullptr;
  }
  return core::List::join(core::List::map(core::List::make<int32_t>(1, 2), [=](int32_t) -> bool { return found == nullptr; }), TEMPER_STR(","), [](bool b) -> core::Str { return core::Boolean::toString(b); });
}

static core::Str bidi() {
  return TEMPER_STR("look\u202Eout");
}

static core::Str word(int32_t n) {
  if (n > 1) {
    return TEMPER_STR("b");
  } else {
    return TEMPER_STR("a");
  }
}

std::shared_ptr<Word> Word::make(core::Str const & text) {
  global_init_init();
  std::shared_ptr<Word> self = std::make_shared<Word>();
  self->text = text;
  return self;
}

core::Str Word::get_text() const {
  return this->text;
}

std::shared_ptr<Joiner> Joiner::make() {
  global_init_init();
  std::shared_ptr<Joiner> self = std::make_shared<Joiner>();
  self->out = core::Str();
  return self;
}

void Joiner::addWord(std::shared_ptr<Word> const & w) {
  this->out = this->out + "w" + w->text;
}

void Joiner::addFlags(std::shared_ptr<std::vector<bool>> const & x) {
  this->out = this->out + "f" + std::to_string(core::List::length<bool>(x));
}

void Joiner::addTexts(std::shared_ptr<std::vector<core::Str>> const & x) {
  this->out = this->out + "t" + std::to_string(core::List::length<core::Str>(x));
}

void Joiner::addMaybes(std::shared_ptr<std::vector<core::NullableParam<core::Str>>> const & x) {
  this->out = this->out + "m" + std::to_string(core::List::length<core::NullableParam<core::Str>>(x));
}

core::Str Joiner::get_out() {
  return this->out;
}

std::shared_ptr<Tag> Tag::make(core::Str const & name) {
  global_init_init();
  std::shared_ptr<Tag> self = std::make_shared<Tag>();
  self->name = name;
  return self;
}

core::Str Tag::toString() const {
  return "<" + this->name + ">";
}

core::Str Tag::get_name() const {
  return this->name;
}

std::shared_ptr<Ticks> Ticks::make() {
  global_init_init();
  std::shared_ptr<Ticks> self = std::make_shared<Ticks>();
  self->n = 0;
  return self;
}

int32_t Ticks::get_count() {
  return this->n;
}

void Ticks::set_count(int32_t c) {
  this->n = c;
}

core::Str Ticks::made() const {
  return "tick" + std::to_string(this->n);
}

std::shared_ptr<Names> Names::make() {
  global_init_init();
  std::shared_ptr<Names> self = std::make_shared<Names>();
  return self;
}

int32_t Names::take(std::shared_ptr<Tag> e) const {
  if (e->name == TEMPER_STR("abc")) {
    return 3;
  } else {
    return 0;
  }
}

core::Str Escapes::escapeFlag(bool b) const {
  std::shared_ptr<Escapes const> self = std::shared_ptr<Escapes const>(shared_from_this(), this);
  return self->escape(b ? TEMPER_STR("T") : TEMPER_STR("F"));
}

std::shared_ptr<Quoting> Quoting::make() {
  global_init_init();
  std::shared_ptr<Quoting> self = std::make_shared<Quoting>();
  return self;
}

core::Str Quoting::escape(core::NullableParam<core::Str> s) const {
  return "'" + (s == nullptr ? TEMPER_STR("null") : s.value) + "'";
}

static core::NullableParam<core::Str> problem(int32_t n) {
  core::NullableParam<core::Str> p = n == 0 ? nullptr : core::panic<core::NullableParam<core::Str>>();
  return p;
}

static core::NullableParam<core::Str> message(int32_t n) {
  core::NullableParam<core::Str> p = n == 0 ? nullptr : n == 1 ? core::NullableParam<core::Str>(TEMPER_STR("one")) : n == 2 ? nullptr : core::panic<core::NullableParam<core::Str>>();
  return p;
}

static int32_t sumParsed(std::shared_ptr<std::vector<core::Str>> const & xs) {
  int32_t total = 0;
  {
    int32_t i = 0;
    while (i < core::List::length<core::Str>(xs)) {
      core::Str x = core::List::get<core::Str>(xs, i);
      int32_t n;
      core::NullableParam<int32_t> held = nullptr;
      try {
        held = core::String::toInt32(x);
      } catch (core::TemperBubble const &) {
      }
      if (held == nullptr) {
        {
          i = core::Int::add(i, 1);
          continue;
        }
      } else {
        n = held.value;
      }
      total = core::Int::add(total, n);
      i = core::Int::add(i, 1);
    }
  }
  {
    int32_t length = core::List::length<core::Str>(xs);
    int32_t i = 0;
    while (i < length) {
      core::Str x = core::List::get<core::Str>(xs, i);
      i = core::Int::add(i, 1);
      int32_t m;
      core::NullableParam<int32_t> held = nullptr;
      try {
        held = core::String::toInt32(x);
      } catch (core::TemperBubble const &) {
      }
      if (held == nullptr) {
        continue;
      } else {
        m = held.value;
      }
      total = core::Int::add(total, core::Int::mul(m, 10));
    }
  }
  return total;
}

static int32_t backwards(std::shared_ptr<std::vector<int32_t>> const & xs) {
  int32_t t = 0;
  {
    int32_t i = core::List::length<int32_t>(xs);
    while (true) {
      i = core::Int::sub(i, 1);
      if (i < 0) {
        break;
      }
      t = core::Int::add(core::Int::mul(t, 10), core::List::get<int32_t>(xs, i));
    }
  }
  return t;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::ticks.construct();
    temper_globals::extras.construct();
    Casing::temper_statics::first.construct();
    Casing::temper_statics::last.construct();
    Casing::temper_statics::mark.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    Casing::temper_statics::first.value = Casing::make(TEMPER_STR("first"));
    Casing::temper_statics::last.value = Casing::named(TEMPER_STR("last"));
    Casing::temper_statics::mark.value = TEMPER_STR("#");
    temper_globals::ticks.value = 0;
    temper_globals::extras.value = core::List::make<int32_t>(1);
    std::shared_ptr<Grid> grid = Grid::make(2, core::List::make<double>(1.0, 2.0, 3.0, 4.0));
    int32_t value2 = libraries::rows(grid->values, 2);
    core::Console::log(console, std::to_string(value2) + " " + core::Float64::toString(half(3.0)) + " " + core::Float64::toString(cube(8.0)));
    core::Console::log(console, core::Float64::toString(libraries::corner(grid)));
    core::Console::log(console, core::Float64::toString(libraries::total(grid->values)));
    core::NullableParam<core::Str> none = text(0);
    core::NullableParam<int32_t> some = count(3);
    core::NullableParam<core::Str> part = none;
    core::NullableParam<int32_t> part2 = some;
    std::shared_ptr<Grid> subject = maybe(0);
    core::NullableParam<int32_t> part3 = subject == nullptr ? nullptr : core::NullableParam<int32_t>(subject->ncols);
    core::Console::log(console, (part == nullptr ? TEMPER_STR("null") : part.value) + " " + (part2 == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(part2.value))) + " " + (part3 == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(part3.value))));
    core::Console::log(console, core::Boolean::toString(maybe(1) != nullptr) + " " + core::Boolean::toString(maybe(0) != nullptr) + " " + core::Boolean::toString(Grid::parse(core::Str()) == nullptr));
    core::Console::log(console, Casing::temper_statics::first.value->toString());
    core::Console::log(console, core::List::join(libraries::mapped(grid), TEMPER_STR(","), [](double x) -> core::Str { return core::Float64::toString(x); }));
    core::Console::log(console, core::List::join(libraries::cubes(grid), TEMPER_STR(","), [](double x) -> core::Str { return core::Float64::toString(x); }));
    std::shared_ptr<Casing> left = casing(1);
    std::shared_ptr<Casing> left2 = casing(0);
    std::shared_ptr<Casing> left3 = casing(0);
    std::shared_ptr<Casing> right = casing(0);
    core::Console::log(console, core::Boolean::toString(left == nullptr ? false : left->eq(Casing::temper_statics::first.value)) + " " + core::Boolean::toString(left2 == nullptr ? false : left2->eq(Casing::temper_statics::first.value)) + " " + core::Boolean::toString(!(left3 == nullptr ? right == nullptr : right == nullptr ? false : left3->eq(right))));
    core::Str value3 = libraries::captured(core::Str());
    core::Console::log(console, value3 + " " + libraries::captured(TEMPER_STR("2.5")));
    core::Console::log(console, core::Boolean::toString(bidi() != TEMPER_STR("lookout")));
    core::Console::log(console, std::to_string(core::Compare::cmp(word(1), word(2))) + " " + std::to_string(core::Compare::cmp(word(2), word(1))) + " " + std::to_string(core::Compare::cmp(word(2), word(3))));
    core::Str named = word(2);
    core::Console::log(console, core::Boolean::toString(named == word(3) && named + "!" != named));
    std::shared_ptr<std::vector<int32_t>> digits = core::List::make<int32_t>(1, 2, 4, 3);
    core::Console::log(console, std::to_string(libraries::byteAt(digits, true, 1)) + " " + std::to_string(libraries::byteAt(digits, false, 0)) + " " + core::Boolean::toString(libraries::ascending(digits)) + " " + std::to_string(libraries::firstDrop(digits)) + " " + std::to_string(libraries::sizeWith<int32_t>(digits)));
    int32_t value4 = tickIf(true);
    int32_t value5 = tickIf(false);
    bool value6 = ticked();
    core::Console::log(console, std::to_string(value4) + " " + std::to_string(value5) + " " + core::Boolean::toString(value6) + " " + std::to_string(tickWhile()));
    core::Str value7 = libraries::shapeName(shapeOf(1));
    core::Str value8 = libraries::shapeName(shapeOf(2));
    core::Str string = value7 + " " + value8 + " " + std::to_string(core::List::length<int32_t>(libraries::sizes(shapeOf(1)))) + " " + std::to_string(core::List::length<int32_t>(libraries::sizes(shapeOf(2)))) + " ";
    core::NullableParam<int32_t> part4 = Dot::make(1)->get_grown();
    core::NullableParam<int32_t> part5 = Dot::make(3)->get_grown();
    core::Console::log(console, string + (part4 == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(part4.value))) + " " + (part5 == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(part5.value))) + " " + Casing::temper_statics::last.value->toString() + " " + Casing::temper_statics::first.value->get_marked());
    std::shared_ptr<Carry> carry = Carry::make(3);
    core::Str value = core::Boolean::toString(carry->carrying) + " " + std::to_string(carry->state) + " " + core::Boolean::toString(Carry::make(0)->carrying) + " ";
    core::NullableParam<core::Str> part6 = libraries::tagged()->label;
    core::NullableParam<core::Str> part7 = libraries::tagged(TEMPER_STR("x"))->label;
    core::Console::log(console, value + (part6 == nullptr ? TEMPER_STR("null") : part6.value) + " " + (part7 == nullptr ? TEMPER_STR("null") : part7.value) + " " + std::to_string(weight(42)) + " " + std::to_string(weight(60)));
    std::shared_ptr<std::vector<bool>> b = core::ListBuilder::make<bool>();
    core::ListBuilder::add<bool>(b, true);
    core::ListBuilder::add<bool>(b, false);
    std::shared_ptr<std::vector<bool>> flags = core::List::toList<bool>(b);
    std::shared_ptr<Joiner> joiner = Joiner::make();
    joiner->addWord(Word::make(TEMPER_STR("a")));
    joiner->addFlags(core::List::make<bool>(true));
    joiner->addTexts(core::List::make<core::Str>(TEMPER_STR("x"), TEMPER_STR("y")));
    std::shared_ptr<std::vector<core::NullableParam<core::Str>>> maybes = core::List::make<core::NullableParam<core::Str>>(nullptr, TEMPER_STR("z"));
    joiner->addMaybes(maybes);
    core::Console::log(console, joiner->out);
    std::shared_ptr<Held<std::shared_ptr<Tag>>> held = Held<std::shared_ptr<Tag>>::make(Tag::make(TEMPER_STR("a")), nullptr);
    std::shared_ptr<Held<std::shared_ptr<Tag>>> other = Held<std::shared_ptr<Tag>>::make(Tag::make(TEMPER_STR("b")), held);
    std::shared_ptr<Held<std::shared_ptr<Tag>>> nobody = nullptr;
    core::Str string2 = held->toString() + " ";
    std::shared_ptr<Held<std::shared_ptr<Tag>>> subject3 = other->next;
    std::shared_ptr<Tag> subject2 = subject3 == nullptr ? nullptr : subject3->item;
    core::Console::log(console, string2 + (subject2 == nullptr ? Tag::make(TEMPER_STR("none")) : subject2)->toString() + " " + core::Boolean::toString(libraries::sameHeld<std::shared_ptr<Tag>>(held, held)) + " " + core::Boolean::toString(libraries::sameHeld<std::shared_ptr<Tag>>(held, other)) + " " + core::Boolean::toString(libraries::sameHeld<std::shared_ptr<Tag>>(nobody, nobody)) + " " + core::Boolean::toString(libraries::sameHeld<std::shared_ptr<Tag>>(nobody, held)));
    core::Str value9 = libraries::effectsOf<int32_t>(TEMPER_STR("ab"), 1, 2);
    core::Console::log(console, value9 + " " + Bundle<core::Str>::make(core::List::make<std::shared_ptr<Effect<core::Str>>>(), TEMPER_STR("2"))->first + " " + std::to_string(core::List::length<bool>(flags)));
    std::shared_ptr<Ticks> counter = Ticks::make();
    std::shared_ptr<Counting<core::Str>> stepper = core::to_base<Counting<core::Str>>(counter);
    stepper->step();
    core::Str value10 = core::List::get<core::Str>(stepper->step(), 0);
    core::Console::log(console, value10 + " " + std::to_string(counter->get_count()) + " " + std::to_string(Names::make()->take(Tag::make(TEMPER_STR("abc")))) + " " + Quoting::make()->escapeFlag(true));
    core::NullableParam<core::Str> subject4 = problem(0);
    core::Str value11 = subject4 == nullptr ? TEMPER_STR("none") : subject4.value;
    core::NullableParam<core::Str> subject5 = message(1);
    core::Str value12 = subject5 == nullptr ? TEMPER_STR("none") : subject5.value;
    core::NullableParam<core::Str> subject6 = message(2);
    core::Str value13 = subject6 == nullptr ? TEMPER_STR("none") : subject6.value;
    int32_t value14 = libraries::sumParsed(core::List::make<core::Str>(TEMPER_STR("1"), TEMPER_STR("x"), TEMPER_STR("2")));
    core::Console::log(console, value11 + " " + value12 + " " + value13 + " " + std::to_string(value14) + " " + std::to_string(libraries::backwards(core::List::make<int32_t>(1, 2, 3))));
  });
}

}  // namespace libraries
