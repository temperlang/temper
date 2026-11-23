// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace nullable_generics {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

template <class T>
struct Slot;
struct Named;

template <class T>
struct Slot : public std::enable_shared_from_this<Slot<T>> {
  T value;
  static std::shared_ptr<Slot<T>> make(T value);
  temper::core::NullableParam<T> kept(bool keep) const;
  T get_value() const;
};

struct Named : public std::enable_shared_from_this<Named> {
  temper::core::Str name;
  static std::shared_ptr<Named> make(temper::core::Str const & name);
  temper::core::Str get_name() const;
};

template <class T>
temper::core::NullableParam<T> firstOrNull(std::shared_ptr<std::vector<T>> const & ls) {
  global_init_init();
  if (temper::core::List::isEmpty<T>(ls)) {
    return nullptr;
  } else {
    return temper::core::List::get<T>(ls, 0);
  }
}

template <class T>
std::shared_ptr<Slot<T>> Slot<T>::make(T value) {
  global_init_init();
  std::shared_ptr<Slot<T>> self = std::make_shared<Slot<T>>();
  self->value = value;
  return self;
}

template <class T>
temper::core::NullableParam<T> Slot<T>::kept(bool keep) const {
  if (keep) {
    return this->value;
  } else {
    return nullptr;
  }
}

template <class T>
T Slot<T>::get_value() const {
  return this->value;
}

template <class T>
T withDefault(T x, temper::core::NullableParam<T> y) {
  global_init_init();
  T y2 = y == nullptr ? x : y.value;
  return y2;
}

template <class T>
T withDefault(T x) {
  return withDefault<T>(x, nullptr);
}

}  // namespace nullable_generics

// init.cpp
#include <nullable-generics/init.hpp>

namespace nullable_generics {

namespace core = temper::core;

std::shared_ptr<Named> Named::make(core::Str const & name) {
  global_init_init();
  std::shared_ptr<Named> self = std::make_shared<Named>();
  self->name = name;
  return self;
}

core::Str Named::get_name() const {
  return this->name;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<core::NullableParam<core::Str>>> strings = core::List::make<core::NullableParam<core::Str>>(nullptr, TEMPER_STR("b"));
    std::shared_ptr<std::vector<core::NullableParam<core::Str>>> noStrings = core::List::make<core::NullableParam<core::Str>>();
    std::shared_ptr<std::vector<core::NullableParam<int32_t>>> ints = core::List::make<core::NullableParam<int32_t>>(nullptr, 2);
    core::NullableParam<core::Str> a = nullable_generics::firstOrNull<core::NullableParam<core::Str>>(strings);
    core::NullableParam<core::Str> b = nullable_generics::firstOrNull<core::NullableParam<core::Str>>(noStrings);
    core::NullableParam<int32_t> c = nullable_generics::firstOrNull<core::NullableParam<int32_t>>(ints);
    core::Console::log(console, core::Boolean::toString(a == nullptr) + " " + core::Boolean::toString(b == nullptr) + " " + core::Boolean::toString(c == nullptr));
    core::NullableParam<core::Str> subject = nullable_generics::firstOrNull<core::Str>(core::List::make<core::Str>(TEMPER_STR("x")));
    core::Console::log(console, subject == nullptr ? TEMPER_STR("none") : subject.value);
    core::NullableParam<int32_t> subject2 = nullable_generics::firstOrNull<core::NullableParam<int32_t>>(core::List::slice<core::NullableParam<int32_t>>(ints, 1, 2));
    int32_t int32 = subject2 == nullptr ? -1 : subject2.value;
    core::NullableParam<int32_t> subject3 = nullable_generics::firstOrNull<int32_t>(core::List::make<int32_t>(3));
    core::Console::log(console, std::to_string(int32) + " " + std::to_string(subject3 == nullptr ? -1 : subject3.value));
    std::shared_ptr<Slot<core::NullableParam<core::Str>>> slot = Slot<core::NullableParam<core::Str>>::make(nullptr);
    core::Console::log(console, core::Boolean::toString(slot->kept(true) == nullptr) + " " + core::Boolean::toString(slot->kept(false) == nullptr));
    core::NullableParam<core::Str> subject4 = Slot<core::NullableParam<core::Str>>::make(TEMPER_STR("s"))->kept(true);
    core::Console::log(console, subject4 == nullptr ? TEMPER_STR("none") : subject4.value);
    std::shared_ptr<std::vector<std::shared_ptr<Named>>> nameds = core::List::make<std::shared_ptr<Named>>(nullptr);
    std::shared_ptr<std::vector<std::shared_ptr<Named>>> noNameds = core::List::make<std::shared_ptr<Named>>();
    std::shared_ptr<Named> n = nullable_generics::firstOrNull<std::shared_ptr<Named>>(core::List::make<std::shared_ptr<Named>>(Named::make(TEMPER_STR("n"))));
    core::Str string = core::Boolean::toString(nullable_generics::firstOrNull<std::shared_ptr<Named>>(nameds) == nullptr) + " " + core::Boolean::toString(nullable_generics::firstOrNull<std::shared_ptr<Named>>(noNameds) == nullptr) + " ";
    core::NullableParam<core::Str> subject5 = n == nullptr ? nullptr : core::NullableParam<core::Str>(n->name);
    core::Console::log(console, string + (subject5 == nullptr ? TEMPER_STR("none") : subject5.value));
    std::shared_ptr<Named> subject7 = nullable_generics::firstOrNull<std::shared_ptr<Named>>(noNameds);
    core::NullableParam<core::Str> subject6 = subject7 == nullptr ? nullptr : core::NullableParam<core::Str>(subject7->name);
    core::Console::log(console, subject6 == nullptr ? TEMPER_STR("none") : subject6.value);
    core::NullableParam<core::Str> none = nullptr;
    core::NullableParam<core::Str> subject8 = nullable_generics::withDefault<core::NullableParam<core::Str>>(TEMPER_STR("a"), none);
    core::Console::log(console, subject8 == nullptr ? TEMPER_STR("null") : subject8.value);
    core::NullableParam<core::Str> subject9 = nullable_generics::withDefault<core::NullableParam<core::Str>>(TEMPER_STR("a"), TEMPER_STR("b"));
    core::Console::log(console, subject9 == nullptr ? TEMPER_STR("null") : subject9.value);
    core::NullableParam<core::Str> subject10 = nullable_generics::withDefault<core::NullableParam<core::Str>>(TEMPER_STR("a"));
    core::Console::log(console, subject10 == nullptr ? TEMPER_STR("null") : subject10.value);
    core::NullableParam<int32_t> subject11 = nullable_generics::withDefault<core::NullableParam<int32_t>>(1, nullptr);
    core::Console::log(console, std::to_string(subject11 == nullptr ? -1 : subject11.value));
    core::Console::log(console, nullable_generics::withDefault<core::Str>(TEMPER_STR("c")) + " " + std::to_string(nullable_generics::withDefault<int32_t>(4, 5)));
  });
}

}  // namespace nullable_generics
