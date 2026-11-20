// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace static_properties_scope {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Simple;

struct Simple : public std::enable_shared_from_this<Simple> {
  struct temper_statics {
    static temper::core::GlobalSlot<temper::core::Str> value;
    static temper::core::GlobalSlot<temper::core::Str> other;
  };
  static temper::core::Str const & value();
  static temper::core::Str const & other();
  static std::shared_ptr<Simple> make();
};

}  // namespace static_properties_scope

// init.cpp
#include <static-properties-scope/init.hpp>

#include <temper-core/console.hpp>

namespace static_properties_scope {

namespace core = temper::core;

core::GlobalSlot<core::Str> Simple::temper_statics::value;

core::Str const & Simple::value() {
  global_init_init();
  return Simple::temper_statics::value.value;
}

core::GlobalSlot<core::Str> Simple::temper_statics::other;

core::Str const & Simple::other() {
  global_init_init();
  return Simple::temper_statics::other.value;
}

std::shared_ptr<Simple> Simple::make() {
  std::shared_ptr<Simple> self = std::make_shared<Simple>();
  return self;
}

static core::Str readSimpleValue() {
  return Simple::temper_statics::value.value;
}

static core::Str readSimpleOther() {
  return Simple::temper_statics::other.value;
}

void global_init_init() {
  core::init_once([] {
    Simple::temper_statics::value.construct();
    Simple::temper_statics::other.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    Simple::temper_statics::value.value = TEMPER_STR("foo");
    Simple::temper_statics::other.value = TEMPER_STR("bar");
    core::Console::log(console, "Simple.value is " + readSimpleValue());
    core::Console::log(console, "Simple.other is " + readSimpleOther());
  });
}

}  // namespace static_properties_scope
