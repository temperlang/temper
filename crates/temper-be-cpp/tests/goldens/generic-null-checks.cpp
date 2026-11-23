// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace generic_null_checks {

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

template <class T>
bool isNull(T x) {
  global_init_init();
  return temper::core::is_null(x);
}

template <class T>
T orElse(T x, T y) {
  global_init_init();
  {
    if (temper::core::is_null(x)) {
      return y;
    } else {
      return x;
    }
  }
}

}  // namespace generic_null_checks

// init.cpp
#include <generic-null-checks/init.hpp>

namespace generic_null_checks {

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
    core::Console::log(console, core::Boolean::toString(generic_null_checks::isNull<core::NullableParam<core::Str>>(nullptr)) + " " + core::Boolean::toString(generic_null_checks::isNull<core::NullableParam<core::Str>>(TEMPER_STR("a"))) + " " + core::Boolean::toString(generic_null_checks::isNull<core::Str>(TEMPER_STR("a"))));
    core::Console::log(console, core::Boolean::toString(generic_null_checks::isNull<core::NullableParam<int32_t>>(nullptr)) + " " + core::Boolean::toString(generic_null_checks::isNull<core::NullableParam<int32_t>>(1)) + " " + core::Boolean::toString(isNull<int32_t>(1)));
    core::Console::log(console, core::Boolean::toString(generic_null_checks::isNull<std::shared_ptr<Named>>(nullptr)) + " " + core::Boolean::toString(generic_null_checks::isNull<std::shared_ptr<Named>>(Named::make(TEMPER_STR("n")))));
    core::NullableParam<core::Str> subject = generic_null_checks::orElse<core::NullableParam<core::Str>>(nullptr, TEMPER_STR("y"));
    core::Console::log(console, subject == nullptr ? TEMPER_STR("null") : subject.value);
    core::Str string = generic_null_checks::orElse<core::Str>(TEMPER_STR("x"), TEMPER_STR("y")) + " ";
    core::NullableParam<int32_t> subject2 = generic_null_checks::orElse<core::NullableParam<int32_t>>(nullptr, 2);
    core::Console::log(console, string + std::to_string(subject2 == nullptr ? -1 : subject2.value));
  });
}

}  // namespace generic_null_checks
