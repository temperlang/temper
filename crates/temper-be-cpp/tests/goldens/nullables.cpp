// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace nullables {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Node;

struct Node : public std::enable_shared_from_this<Node> {
  int32_t value;
  std::shared_ptr<Node> next;
  static std::shared_ptr<Node> make(int32_t value, std::shared_ptr<Node> const & next);
  int32_t last() const;
  int32_t get_value() const;
  std::shared_ptr<Node> get_next() const;
};

temper::core::Str describe(temper::core::NullableParam<int32_t> x);
temper::core::NullableParam<temper::core::Str> firstWord(temper::core::NullableParam<temper::core::Str> s);
int32_t stops();

}  // namespace nullables

// init.cpp
#include <nullables/init.hpp>

namespace nullables {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Node> Node::make(int32_t value, std::shared_ptr<Node> const & next) {
  global_init_init();
  std::shared_ptr<Node> self = std::make_shared<Node>();
  self->value = value;
  self->next = next;
  return self;
}

int32_t Node::last() const {
  std::shared_ptr<Node const> self = shared_from_this();
  std::shared_ptr<Node> n = self->next;
  if (n == nullptr) {
    return self->value;
  } else {
    return n->last();
  }
}

int32_t Node::get_value() const {
  return this->value;
}

std::shared_ptr<Node> Node::get_next() const {
  return this->next;
}

core::Str describe(core::NullableParam<int32_t> x) {
  global_init_init();
  if (x == nullptr) {
    return TEMPER_STR("none");
  }
  return "some " + std::to_string(core::Int::add(x.value, 1));
}

core::NullableParam<core::Str> firstWord(core::NullableParam<core::Str> s) {
  global_init_init();
  if (s != nullptr && s.value != core::Str()) {
    return s;
  } else {
    return nullptr;
  }
}

static bool same(core::NullableParam<core::Str> a, core::NullableParam<core::Str> b) {
  return a == b;
}

static bool sameInt(core::NullableParam<int32_t> a, core::NullableParam<int32_t> b) {
  return a == b;
}

static int32_t parse(core::Str const & s) {
  try {
    return core::String::toInt32(s);
  } catch (core::TemperBubble const &) {
    return -1;
  }
}

static int32_t halved(core::Str const & s, int32_t d) {
  try {
    return core::Int::div_wrap(core::String::toInt32(s), d);
  } catch (core::TemperBubble const &) {
    return -1;
  }
}

static int32_t positive(int32_t n) {
  if (n < 0) {
    core::bubble();
  }
  return n;
}

static int32_t checked(int32_t n) {
  try {
    return positive(n);
  } catch (core::TemperBubble const &) {
    core::panic();
  }
}

int32_t stops() {
  global_init_init();
  core::panic();
}

static int32_t orZero(core::NullableParam<int32_t> x) {
  {
    if (x == nullptr) {
      return 0;
    } else {
      return x.value;
    }
  }
}

static core::Str reason(int32_t n, core::NullableParam<core::Str> why) {
  try {
    positive(n);
    return TEMPER_STR("ok");
  } catch (core::TemperBubble const &) {
    return why == nullptr ? TEMPER_STR("fails") : why.value;
  }
}

static void varying() {
  core::NullableParam<int32_t> maybe = nullptr;
  core::Console::log(temper_globals::console.value, core::Boolean::toString(maybe == nullptr));
  maybe = 3;
  core::Console::log(temper_globals::console.value, core::Boolean::toString(maybe != nullptr));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    core::Console::log(temper_globals::console.value, nullables::describe(nullptr));
    core::Console::log(temper_globals::console.value, nullables::describe(41));
    core::NullableParam<core::Str> subject = nullables::firstWord(TEMPER_STR("hi"));
    core::Console::log(temper_globals::console.value, subject == nullptr ? TEMPER_STR("nothing") : subject.value);
    core::NullableParam<core::Str> subject2 = nullables::firstWord(core::Str());
    core::Console::log(temper_globals::console.value, subject2 == nullptr ? TEMPER_STR("nothing") : subject2.value);
    core::Console::log(temper_globals::console.value, core::Boolean::toString(nullables::same(nullptr, nullptr)) + " " + core::Boolean::toString(nullables::same(TEMPER_STR("a"), nullptr)) + " " + core::Boolean::toString(nullables::same(nullptr, TEMPER_STR("a"))) + " " + core::Boolean::toString(nullables::same(TEMPER_STR("a"), TEMPER_STR("a"))));
    core::Console::log(temper_globals::console.value, core::Boolean::toString(nullables::sameInt(1, 2)) + " " + core::Boolean::toString(nullables::sameInt(3, 3)) + " " + core::Boolean::toString(nullables::sameInt(nullptr, 3)));
    int32_t int32 = nullables::parse(TEMPER_STR("12"));
    core::Console::log(temper_globals::console.value, std::to_string(int32) + " " + std::to_string(nullables::parse(TEMPER_STR("x"))));
    int32_t value2 = nullables::halved(TEMPER_STR("12"), 2);
    int32_t value3 = nullables::halved(TEMPER_STR("x"), 2);
    core::Console::log(temper_globals::console.value, std::to_string(value2) + " " + std::to_string(value3) + " " + std::to_string(nullables::halved(TEMPER_STR("12"), 0)));
    core::Console::log(temper_globals::console.value, std::to_string(checked(7)));
    core::Console::log(temper_globals::console.value, std::to_string(nullables::orZero(nullptr)) + " " + std::to_string(nullables::orZero(5)));
    varying();
    std::shared_ptr<Node> list = Node::make(1, Node::make(2, Node::make(3, nullptr)));
    core::Console::log(temper_globals::console.value, std::to_string(list->last()));
    std::shared_ptr<Node> tail = list->next;
    if (tail != nullptr) {
      core::Console::log(temper_globals::console.value, std::to_string(tail->value));
    }
    try {
      positive(-1);
    } catch (core::TemperBubble const &) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("negative"));
    }
    int32_t fallback;
    try {
      fallback = positive(-2);
    } catch (core::TemperBubble const &) {
      fallback = 0;
    }
    core::Console::log(temper_globals::console.value, std::to_string(fallback));
    core::Console::log(temper_globals::console.value, nullables::reason(1, nullptr) + " " + nullables::reason(-1, nullptr) + " " + nullables::reason(-1, TEMPER_STR("bad")));
  });
}

}  // namespace nullables
