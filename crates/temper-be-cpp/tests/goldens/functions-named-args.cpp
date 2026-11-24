// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace named_args {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Greet;
struct Person;
struct CalcOptions;

struct Greet : public std::enable_shared_from_this<Greet> {
  static std::shared_ptr<Greet> make(temper::core::NullableParam<temper::core::Str> message, temper::core::NullableParam<bool> repeat);
  static std::shared_ptr<Greet> make(temper::core::NullableParam<temper::core::Str> message);
  static std::shared_ptr<Greet> make();
};

struct Person : public std::enable_shared_from_this<Person> {
  temper::core::Str name;
  int32_t age;
  static std::shared_ptr<Person> make(temper::core::NullableParam<temper::core::Str> name, temper::core::NullableParam<int32_t> age);
  static std::shared_ptr<Person> make(temper::core::NullableParam<temper::core::Str> name);
  static std::shared_ptr<Person> make();
  temper::core::Str toString() const;
  void greet(temper::core::NullableParam<temper::core::Str> greeting) const;
  void greet() const;
  temper::core::Str get_name() const;
  int32_t get_age() const;
};

struct CalcOptions : public std::enable_shared_from_this<CalcOptions> {
  int32_t scale;
  int32_t origin;
  static std::shared_ptr<CalcOptions> make(temper::core::NullableParam<int32_t> scale, temper::core::NullableParam<int32_t> origin);
  static std::shared_ptr<CalcOptions> make(temper::core::NullableParam<int32_t> scale);
  static std::shared_ptr<CalcOptions> make();
  int32_t get_scale() const;
  int32_t get_origin() const;
};

}  // namespace named_args

// init.cpp
#include <named-args/init.hpp>

#include <mutex>

namespace named_args {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<core::Str> message;
static core::GlobalSlot<bool> repeat;

}  // namespace temper_globals

static std::mutex moduleMutex;

std::shared_ptr<Greet> Greet::make(core::NullableParam<core::Str> message, core::NullableParam<bool> repeat) {
  global_init_init();
  std::shared_ptr<Greet> self = std::make_shared<Greet>();
  core::Str message2 = message == nullptr ? TEMPER_STR("Hi!") : message.value;
  bool repeat2 = repeat == nullptr ? false : repeat.value;
  core::Console::log(temper_globals::console.value, message2);
  if (repeat2) {
    core::Console::log(temper_globals::console.value, message2);
  }
  return self;
}

std::shared_ptr<Greet> Greet::make(core::NullableParam<core::Str> message) {
  return make(message, nullptr);
}

std::shared_ptr<Greet> Greet::make() {
  return make(nullptr, nullptr);
}

static core::Str changeRepeat() {
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::repeat.value = true;
  return temper_globals::message.value;
}

static bool changeMessage() {
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::message.value = TEMPER_STR("Namaste!");
  return temper_globals::repeat.value;
}

std::shared_ptr<Person> Person::make(core::NullableParam<core::Str> name, core::NullableParam<int32_t> age) {
  std::shared_ptr<Person> self = std::make_shared<Person>();
  core::Str name2 = name == nullptr ? TEMPER_STR("Alice") : name.value;
  int32_t age2 = age == nullptr ? 40 : age.value;
  self->name = name2;
  self->age = age2;
  return self;
}

std::shared_ptr<Person> Person::make(core::NullableParam<core::Str> name) {
  return make(name, nullptr);
}

std::shared_ptr<Person> Person::make() {
  return make(nullptr, nullptr);
}

core::Str Person::toString() const {
  return "{ class: Person, name: " + this->name + ", age: " + std::to_string(this->age) + " }";
}

void Person::greet(core::NullableParam<core::Str> greeting) const {
  core::Str greeting2 = greeting == nullptr ? TEMPER_STR("Hi") : greeting.value;
  core::Console::log(temper_globals::console.value, greeting2 + ", " + this->name);
}

void Person::greet() const {
  return greet(nullptr);
}

core::Str Person::get_name() const {
  return this->name;
}

int32_t Person::get_age() const {
  return this->age;
}

std::shared_ptr<CalcOptions> CalcOptions::make(core::NullableParam<int32_t> scale, core::NullableParam<int32_t> origin) {
  std::shared_ptr<CalcOptions> self = std::make_shared<CalcOptions>();
  int32_t scale2 = scale == nullptr ? 1 : scale.value;
  int32_t origin2 = origin == nullptr ? 0 : origin.value;
  self->scale = scale2;
  self->origin = origin2;
  return self;
}

std::shared_ptr<CalcOptions> CalcOptions::make(core::NullableParam<int32_t> scale) {
  return make(scale, nullptr);
}

std::shared_ptr<CalcOptions> CalcOptions::make() {
  return make(nullptr, nullptr);
}

int32_t CalcOptions::get_scale() const {
  return this->scale;
}

int32_t CalcOptions::get_origin() const {
  return this->origin;
}

template <class T>
static T calc(int32_t n, std::function<T(int32_t)> const & transform, std::shared_ptr<CalcOptions> const & options) {
  std::shared_ptr<CalcOptions> options2 = options == nullptr ? CalcOptions::make() : options;
  int32_t origin = options2->origin;
  int32_t scale = options2->scale;
  return transform(core::Int::mul(core::Int::sub(n, origin), scale));
}

template <class T>
static T calc(int32_t n, std::function<T(int32_t)> const & transform) {
  return calc(n, transform, nullptr);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::message.construct();
    temper_globals::repeat.construct();
    temper_globals::console.value = core::Console::get_console();
    Greet::make();
    Greet::make(TEMPER_STR("Bye!"));
    Greet::make(TEMPER_STR("Hola!"), true);
    Greet::make(TEMPER_STR("Salut!"));
    Greet::make(nullptr, true);
    Greet::make(nullptr, true);
    {
      bool arg = true;
      Greet::make(TEMPER_STR("Ni hao!"), arg);
    }
    temper_globals::message.value = TEMPER_STR("Hi!");
    temper_globals::repeat.value = false;
    {
      bool arg2 = changeMessage();
      core::Str arg3 = changeRepeat();
      Greet::make(arg3, arg2);
    }
    core::Console::log(temper_globals::console.value, Person::make()->toString());
    core::Console::log(temper_globals::console.value, Person::make(TEMPER_STR("Bob"))->toString());
    core::Console::log(temper_globals::console.value, Person::make(TEMPER_STR("Carrie"), 50)->toString());
    Person::make(TEMPER_STR("Dan"))->greet(TEMPER_STR("Hallo"));
    core::Console::log(temper_globals::console.value, named_args::calc<core::Str>(2, [](int32_t n) -> core::Str { return std::to_string(n); }));
    core::Console::log(temper_globals::console.value, named_args::calc<core::Str>(2, [](int32_t n) -> core::Str { return std::to_string(n); }, CalcOptions::make(3, 4)));
    core::Console::log(temper_globals::console.value, named_args::calc<core::Str>(2, [](int32_t n) -> core::Str { return std::to_string(n); }, CalcOptions::make(3)));
    core::Console::log(temper_globals::console.value, named_args::calc<core::Str>(2, [](int32_t n) -> core::Str { return std::to_string(n); }, CalcOptions::make(nullptr, 4)));
    core::Console::log(temper_globals::console.value, named_args::calc<core::Str>(2, [](int32_t n) -> core::Str { return "<" + std::to_string(n) + ">"; }, CalcOptions::make(5)));
    auto fn6 = [](int32_t n) -> std::shared_ptr<core::Empty> {
      core::Console::log(temper_globals::console.value, std::to_string(n));
      return core::empty();
    };
    named_args::calc<std::shared_ptr<core::Empty>>(2, fn6, CalcOptions::make(-2));
  });
}

}  // namespace named_args
