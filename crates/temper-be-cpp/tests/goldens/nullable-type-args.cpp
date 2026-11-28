// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace nullable_type_args {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Named;

struct Named : public std::enable_shared_from_this<Named> {
  temper::core::Str name;
  static std::shared_ptr<Named> make(temper::core::Str const & name);
  temper::core::Str get_name() const;
};

}  // namespace nullable_type_args

// init.cpp
#include <nullable-type-args/init.hpp>

namespace nullable_type_args {

namespace core = temper::core;

std::shared_ptr<Named> Named::make(core::Str const & name) {
  std::shared_ptr<Named> self = std::make_shared<Named>();
  self->name = name;
  return self;
}

core::Str Named::get_name() const {
  return this->name;
}

template <class A, class B>
static std::shared_ptr<core::Pair<A, core::Nullable<B>>> halfPair(A a) {
  return core::PairFactory::make<A, core::Nullable<B>>(a, nullptr);
}

template <class A, class B>
static std::shared_ptr<core::Pair<A, core::Nullable<B>>> halfNamed(A a) {
  return core::PairFactory::make<A, core::Nullable<B>>(a, nullptr);
}

template <class T>
static T known(core::Nullable<T> x) {
  return core::not_null(x);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<core::Pair<int32_t, std::shared_ptr<core::AnyValueBase>>> half = halfPair<int32_t, std::shared_ptr<core::AnyValueBase>>(8);
    std::shared_ptr<core::Pair<int32_t, std::shared_ptr<Named>>> halfThree = halfNamed<int32_t, std::shared_ptr<Named>>(3);
    std::shared_ptr<core::Pair<int32_t, std::shared_ptr<Named>>> halfMade = halfPair<int32_t, std::shared_ptr<Named>>(9);
    std::shared_ptr<core::Pair<int32_t, core::NullableParam<int32_t>>> halfInt = halfPair<int32_t, int32_t>(1);
    core::Str string = std::to_string(half->key) + " " + core::Boolean::toString(half->value == nullptr) + " " + std::to_string(halfThree->key) + " " + core::Boolean::toString(halfMade->value == nullptr) + " ";
    core::NullableParam<int32_t> subject = halfInt->value;
    core::Console::log(console, string + std::to_string(subject == nullptr ? -1 : subject.value));
    std::shared_ptr<Named> named = nullptr;
    std::shared_ptr<Named> fallback = Named::make(TEMPER_STR("bubbled"));
    std::shared_ptr<Named> got;
    try {
      got = nullable_type_args::known<std::shared_ptr<Named>>(named);
    } catch (core::TemperBubble const &) {
      got = fallback;
    }
    core::Console::log(console, nullable_type_args::known<std::shared_ptr<Named>>(Named::make(TEMPER_STR("n")))->name + " " + got->name + " " + std::to_string(nullable_type_args::known<int32_t>(2)));
  });
}

}  // namespace nullable_type_args
