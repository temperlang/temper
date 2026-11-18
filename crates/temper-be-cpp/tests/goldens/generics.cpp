// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace generics {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

template <class T>
struct Holder;
template <class T>
struct Box;
struct Named;
struct Counter;
template <class T>
struct Shelf;
struct Scene;
struct Stage;

template <class T>
struct Holder : public virtual temper::core::AnyValueBase {
  virtual T get_held() const = 0;
};

template <class T>
struct Box : public virtual Holder<T> {
  T value;
  static std::shared_ptr<Box<T>> make(T value);
  T get_held() const override;
  std::shared_ptr<temper::core::Pair<T, int32_t>> pairWith(int32_t other) const;
  T get_value() const;
};

struct Named : public std::enable_shared_from_this<Named> {
  temper::core::Str name;
  static std::shared_ptr<Named> make(temper::core::Str const & name);
  temper::core::Str get_name() const;
};

struct Counter : public std::enable_shared_from_this<Counter> {
  int32_t n;
  static std::shared_ptr<Counter> make(int32_t n);
  int32_t get_n();
};

template <class T>
struct Shelf : public std::enable_shared_from_this<Shelf<T>> {
  std::shared_ptr<std::vector<T>> items;
  static std::shared_ptr<Shelf<T>> make(std::shared_ptr<std::vector<T>> const & items);
  temper::core::NullableParam<temper::core::Str> firstName() const;
  template <class TName>
  int32_t tally(int32_t extra) const;
  std::shared_ptr<std::vector<T>> get_items() const;
};

struct Scene : public virtual temper::core::AnyValueBase {};

struct Stage : public virtual Scene {
  static std::shared_ptr<Stage> make();
};

bool noneIn(int32_t length);

template <class T>
std::shared_ptr<Box<T>> Box<T>::make(T value) {
  global_init_init();
  std::shared_ptr<Box<T>> self = std::make_shared<Box<T>>();
  self->value = value;
  return self;
}

template <class T>
T Box<T>::get_held() const {
  return this->value;
}

template <class T>
std::shared_ptr<temper::core::Pair<T, int32_t>> Box<T>::pairWith(int32_t other) const {
  return temper::core::PairFactory::make<T, int32_t>(this->value, other);
}

template <class T>
T Box<T>::get_value() const {
  return this->value;
}

template <class T>
std::shared_ptr<Shelf<T>> Shelf<T>::make(std::shared_ptr<std::vector<T>> const & items) {
  global_init_init();
  std::shared_ptr<Shelf<T>> self = std::make_shared<Shelf<T>>();
  self->items = items;
  return self;
}

template <class T>
temper::core::NullableParam<temper::core::Str> Shelf<T>::firstName() const {
  if (temper::core::List::isEmpty<T>(this->items)) {
    return nullptr;
  } else {
    return temper::core::List::get<T>(this->items, 0)->name;
  }
}

template <class T>
template <class TName>
int32_t Shelf<T>::tally(int32_t extra) const {
  return temper::core::Int::add(temper::core::List::length<T>(this->items), extra);
}

template <class T>
std::shared_ptr<std::vector<T>> Shelf<T>::get_items() const {
  return this->items;
}

template <class T>
T first(std::shared_ptr<std::vector<T>> const & items, T fallback) {
  global_init_init();
  if (noneIn(temper::core::List::length<T>(items))) {
    return fallback;
  } else {
    return temper::core::List::get<T>(items, 0);
  }
}

}  // namespace generics

// init.cpp
#include <generics/init.hpp>

namespace generics {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>>> tally;

}  // namespace temper_globals

std::shared_ptr<Named> Named::make(core::Str const & name) {
  global_init_init();
  std::shared_ptr<Named> self = std::make_shared<Named>();
  self->name = name;
  return self;
}

core::Str Named::get_name() const {
  return this->name;
}

std::shared_ptr<Counter> Counter::make(int32_t n) {
  global_init_init();
  std::shared_ptr<Counter> self = std::make_shared<Counter>();
  self->n = n;
  return self;
}

int32_t Counter::get_n() {
  return this->n;
}

std::shared_ptr<Stage> Stage::make() {
  global_init_init();
  std::shared_ptr<Stage> self = std::make_shared<Stage>();
  return self;
}

template <class S, class T>
static T tagged(T x) {
  return x;
}

template <class T>
static int32_t countOf(int32_t n) {
  std::shared_ptr<std::vector<T>> b = core::ListBuilder::make<T>();
  return core::Int::add(core::List::length<T>(b), n);
}

template <class S, class T>
static T relayed(T x) {
  return generics::tagged<S, T>(x);
}

bool noneIn(int32_t length) {
  return length == 0;
}

template <class T>
static T held(std::shared_ptr<Holder<T>> const & h) {
  return h->get_held();
}

static int32_t total(std::shared_ptr<std::vector<int32_t>> const & ns) {
  int32_t sum = 0;
  {
    int32_t i = 0;
    while (i < core::List::length<int32_t>(ns)) {
      sum = core::Int::add(sum, core::List::get<int32_t>(ns, i));
      i = core::Int::add(i, 1);
    }
  }
  return sum;
}

static void indexed() {
  std::shared_ptr<std::vector<int32_t>> counts = core::ListBuilder::make<int32_t>();
  core::ListBuilder::add<int32_t>(counts, 1);
  core::ListBuilder::add<int32_t>(counts, 10);
  int32_t at = 0;
  {
    int32_t k = at;
    core::ListBuilder::set<int32_t>(counts, k, core::Int::add(core::List::get<int32_t>(counts, k), 4));
  }
  {
    core::ListBuilder::set<int32_t>(counts, 1, core::Int::mul(core::List::get<int32_t>(counts, 1), 3));
  }
  int32_t t = at;
  at = core::Int::add(t, 1);
  int32_t k2 = t;
  int32_t t2 = core::List::get<int32_t>(counts, k2);
  core::ListBuilder::set<int32_t>(counts, k2, core::Int::add(t2, 1));
  int32_t was = t2;
  int32_t k3 = at;
  int32_t t3 = core::Int::add(core::List::get<int32_t>(counts, k3), 1);
  core::ListBuilder::set<int32_t>(counts, k3, t3);
  int32_t pre = t3;
  int32_t t4 = core::List::get<int32_t>(counts, 0);
  core::ListBuilder::set<int32_t>(counts, 0, core::Int::sub(t4, 1));
  int32_t post = t4;
  int32_t t5 = core::Int::sub(core::List::get<int32_t>(counts, 0), 1);
  core::ListBuilder::set<int32_t>(counts, 0, t5);
  int32_t down = t5;
  core::Console::log(temper_globals::console.value, std::to_string(was) + " " + std::to_string(pre) + " " + std::to_string(post) + " " + std::to_string(down) + " " + std::to_string(at));
}

static void both() {
  int32_t x = 1;
  int32_t y = 10;
  int32_t t = x;
  x = core::Int::add(t, 1);
  int32_t t2 = y;
  y = core::Int::add(t2, 1);
  int32_t locals = core::Int::add(t, t2);
  std::shared_ptr<Counter> p = Counter::make(100);
  std::shared_ptr<Counter> q = Counter::make(1000);
  int32_t t3 = p->n;
  p->n = core::Int::add(t3, 1);
  int32_t int32 = t3;
  int32_t t4 = q->n;
  q->n = core::Int::add(t4, 1);
  int32_t fields = core::Int::add(int32, t4);
  int32_t t5 = p->n;
  p->n = core::Int::add(t5, 1);
  int32_t value2 = t5;
  int32_t t6 = p->n;
  p->n = core::Int::add(t6, 1);
  int32_t same = core::Int::add(value2, t6);
  std::shared_ptr<std::vector<int32_t>> a = core::ListBuilder::make<int32_t>();
  core::ListBuilder::add<int32_t>(a, 3);
  std::shared_ptr<std::vector<int32_t>> b = core::ListBuilder::make<int32_t>();
  core::ListBuilder::add<int32_t>(b, 30);
  int32_t i = 0;
  int32_t k = i;
  int32_t t7 = core::List::get<int32_t>(a, k);
  core::ListBuilder::set<int32_t>(a, k, core::Int::add(t7, 1));
  int32_t value3 = t7;
  int32_t k2 = i;
  int32_t t8 = core::List::get<int32_t>(b, k2);
  core::ListBuilder::set<int32_t>(b, k2, core::Int::add(t8, 1));
  int32_t items = core::Int::add(value3, t8);
  core::Console::log(temper_globals::console.value, std::to_string(locals) + " " + std::to_string(x) + " " + std::to_string(y) + " " + std::to_string(fields) + " " + std::to_string(same) + " " + std::to_string(p->n) + " " + std::to_string(q->n) + " " + std::to_string(items) + " " + std::to_string(core::List::get<int32_t>(a, 0)) + " " + std::to_string(core::List::get<int32_t>(b, 0)));
}

static int32_t bump(core::Str const & key) {
  {
    core::MapBuilder::set<core::Str, int32_t>(temper_globals::tally.value, key, core::Int::sub(core::Mapped::get<core::Str, int32_t>(temper_globals::tally.value, key), 1));
  }
  int32_t t = core::Mapped::get<core::Str, int32_t>(temper_globals::tally.value, key);
  core::MapBuilder::set<core::Str, int32_t>(temper_globals::tally.value, key, core::Int::add(t, 1));
  int32_t before = t;
  int32_t t2 = core::Int::add(core::Mapped::get<core::Str, int32_t>(temper_globals::tally.value, key), 1);
  core::MapBuilder::set<core::Str, int32_t>(temper_globals::tally.value, key, t2);
  int32_t after = t2;
  return core::Int::add(before, after);
}

static bool firstSame(std::shared_ptr<std::vector<int32_t>> const & a, std::shared_ptr<std::vector<int32_t>> const & b) {
  return core::List::get<int32_t>(a, 0) == core::List::get<int32_t>(b, 0);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::tally.construct();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<std::vector<int32_t>> ns = core::List::make<int32_t>(1, 2, 3);
    std::shared_ptr<std::vector<int32_t>> none = core::List::make<int32_t>();
    core::Console::log(temper_globals::console.value, std::to_string(generics::total(ns)) + " " + std::to_string(generics::first<int32_t>(ns, 0)) + " " + std::to_string(generics::first<int32_t>(none, -1)));
    core::Console::log(temper_globals::console.value, generics::first<core::Str>(core::List::make<core::Str>(TEMPER_STR("a"), TEMPER_STR("b")), TEMPER_STR("z")));
    std::shared_ptr<Box<core::Str>> box = Box<core::Str>::make(TEMPER_STR("boxed"));
    core::Console::log(temper_globals::console.value, generics::held<core::Str>(core::to_base<Holder<core::Str>>(box)));
    std::shared_ptr<core::Pair<core::Str, int32_t>> pair = box->pairWith(7);
    core::Console::log(temper_globals::console.value, pair->key + " " + std::to_string(pair->value));
    std::shared_ptr<Shelf<std::shared_ptr<Named>>> shelf = Shelf<std::shared_ptr<Named>>::make(core::List::make<std::shared_ptr<Named>>(Named::make(TEMPER_STR("ann")), Named::make(TEMPER_STR("bo"))));
    core::NullableParam<core::Str> subject = shelf->firstName();
    core::Console::log(temper_globals::console.value, subject == nullptr ? TEMPER_STR("none") : subject.value);
    std::shared_ptr<std::vector<std::shared_ptr<Named>>> noNames = core::List::make<std::shared_ptr<Named>>();
    std::shared_ptr<Shelf<std::shared_ptr<Named>>> empty = Shelf<std::shared_ptr<Named>>::make(noNames);
    core::NullableParam<core::Str> subject2 = empty->firstName();
    core::Console::log(temper_globals::console.value, subject2 == nullptr ? TEMPER_STR("none") : subject2.value);
    int32_t n = core::Int::add(shelf->tally<std::shared_ptr<Named>>(1), shelf->tally<std::shared_ptr<Named>>(2));
    core::Console::log(temper_globals::console.value, std::to_string(tagged<std::shared_ptr<Scene>, int32_t>(4)) + " " + generics::tagged<std::shared_ptr<Scene>, core::Str>(TEMPER_STR("t")) + " " + std::to_string(countOf<std::shared_ptr<core::AnyValueBase>>(3)) + " " + std::to_string(relayed<std::shared_ptr<Scene>, int32_t>(5)) + " " + std::to_string(n));
    std::shared_ptr<std::vector<core::Str>> builder = core::ListBuilder::make<core::Str>();
    core::ListBuilder::add<core::Str>(builder, TEMPER_STR("x"));
    core::ListBuilder::add<core::Str>(builder, TEMPER_STR("y"));
    core::ListBuilder::add<core::Str>(builder, TEMPER_STR("w"), 0);
    int32_t int32 = core::List::length<core::Str>(builder);
    core::Str get = core::List::get<core::Str>(builder, 0);
    core::Console::log(temper_globals::console.value, std::to_string(int32) + " " + get + " " + core::ListBuilder::removeLast<core::Str>(builder));
    std::shared_ptr<std::vector<core::Str>> built = core::List::toList<core::Str>(builder);
    int32_t value2 = core::List::length<core::Str>(built);
    core::Str string = core::List::getOr<core::Str>(built, 5, TEMPER_STR("?"));
    core::Console::log(temper_globals::console.value, std::to_string(value2) + " " + string + " " + core::List::get<core::Str>(core::List::slice<core::Str>(built, 1, 2), 0));
    std::shared_ptr<core::Pair<core::Str, int32_t>> value3 = core::PairFactory::make<core::Str, int32_t>(TEMPER_STR("a"), 1);
    std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> m = core::Map::make<core::Str, int32_t>(core::List::make<std::shared_ptr<core::Pair<core::Str, int32_t>>>(value3, core::PairFactory::make<core::Str, int32_t>(TEMPER_STR("b"), 2)));
    int32_t value4;
    try {
      value4 = core::Mapped::get<core::Str, int32_t>(m, TEMPER_STR("b"));
    } catch (core::TemperBubble const &) {
      value4 = -1;
    }
    int32_t value5;
    try {
      value5 = core::Mapped::get<core::Str, int32_t>(m, TEMPER_STR("c"));
    } catch (core::TemperBubble const &) {
      value5 = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(value4) + " " + std::to_string(value5) + " " + core::Boolean::toString(core::Mapped::has<core::Str, int32_t>(m, TEMPER_STR("a"))) + " " + std::to_string(core::Mapped::length<core::Str, int32_t>(m)));
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::NullableParam<int32_t>>> mb = core::Map::make<core::Str, core::NullableParam<int32_t>>();
    core::MapBuilder::set<core::Str, core::NullableParam<int32_t>>(mb, TEMPER_STR("q"), 5);
    core::MapBuilder::set<core::Str, core::NullableParam<int32_t>>(mb, TEMPER_STR("n"), nullptr);
    core::NullableParam<int32_t> subject3 = core::Mapped::getOr<core::Str, core::NullableParam<int32_t>>(mb, TEMPER_STR("q"), 0);
    core::NullableParam<int32_t> subject4 = core::Mapped::getOr<core::Str, core::NullableParam<int32_t>>(mb, TEMPER_STR("n"), 0);
    core::Console::log(temper_globals::console.value, std::to_string(subject3 == nullptr ? -2 : subject3.value) + " " + std::to_string(subject4 == nullptr ? -2 : subject4.value) + " " + core::Boolean::toString(core::Mapped::has<core::Str, core::NullableParam<int32_t>>(mb, TEMPER_STR("n"))));
    core::NullableParam<int32_t> subject6;
    try {
      subject6 = core::MapBuilder::remove<core::Str, core::NullableParam<int32_t>>(mb, TEMPER_STR("q"));
    } catch (core::TemperBubble const &) {
      subject6 = 0;
    }
    core::NullableParam<core::Str> subject5 = subject6 == nullptr ? nullptr : core::NullableParam<core::Str>(std::to_string(subject6.value));
    core::Console::log(temper_globals::console.value, subject5 == nullptr ? TEMPER_STR("null") : subject5.value);
    core::Console::log(temper_globals::console.value, std::to_string(core::List::length<core::Str>(core::Mapped::keys<core::Str, core::NullableParam<int32_t>>(mb))));
    indexed();
    both();
    temper_globals::tally.value = core::Map::make<core::Str, int32_t>();
    core::MapBuilder::set<core::Str, int32_t>(temper_globals::tally.value, TEMPER_STR("k"), 2);
    int32_t value6;
    try {
      value6 = generics::bump(TEMPER_STR("k"));
    } catch (core::TemperBubble const &) {
      value6 = -1;
    }
    int32_t value7 = value6;
    int32_t value8;
    try {
      value8 = generics::bump(TEMPER_STR("z"));
    } catch (core::TemperBubble const &) {
      value8 = -1;
    }
    int32_t value9 = value8;
    int32_t value10;
    try {
      value10 = core::Mapped::get<core::Str, int32_t>(temper_globals::tally.value, TEMPER_STR("k"));
    } catch (core::TemperBubble const &) {
      value10 = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(value7) + " " + std::to_string(value9) + " " + std::to_string(value10));
    core::Console::log(temper_globals::console.value, core::Boolean::toString(generics::firstSame(core::List::make<int32_t>(1000), core::List::make<int32_t>(1000))) + " " + core::Boolean::toString(generics::firstSame(core::List::make<int32_t>(1000), core::List::make<int32_t>(7))));
  });
}

}  // namespace generics
