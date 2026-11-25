// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace count_failures {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct CountError;
struct BadCount;

struct CountError : public virtual temper::core::Failure {};

struct BadCount : public virtual CountError {
  temper::core::Str text;
  static std::shared_ptr<BadCount> make(temper::core::Str const & text);
  temper::core::Str get_message() const override;
  temper::core::Str get_text() const;
};

temper::core::Expected<int32_t, std::shared_ptr<BadCount>> parseCount(temper::core::Str const & text);

}  // namespace count_failures

// init.cpp
#include <count-failures/init.hpp>

namespace count_failures {

namespace core = temper::core;

std::shared_ptr<BadCount> BadCount::make(core::Str const & text) {
  std::shared_ptr<BadCount> self = std::make_shared<BadCount>();
  self->text = text;
  return self;
}

core::Str BadCount::get_message() const {
  return "bad count (text: " + this->text + ")";
}

core::Str BadCount::get_text() const {
  return this->text;
}

core::Expected<int32_t, std::shared_ptr<BadCount>> parseCount(core::Str const & text) {
  {
    if (text == TEMPER_STR("zero")) {
      return 0;
    } else if (text == TEMPER_STR("two")) {
      return 2;
    } else {
      return core::unexpected(BadCount::make(text));
    }
  }
}

void global_init_init() {}

}  // namespace count_failures
