// init.hpp
#pragma once

namespace is_empty {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace is_empty

// init.cpp
#include <is-empty/init.hpp>

#include <temper-core/str.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>
#include <memory>

namespace is_empty {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Str emptyString = core::Str();
    core::Str foo = TEMPER_STR("foo");
    core::Console::log(console, emptyString.empty() ? TEMPER_STR("emptyString is empty") : TEMPER_STR("emptyString is not empty"));
    core::Console::log(console, foo.empty() ? TEMPER_STR("foo is empty") : TEMPER_STR("foo is not empty"));
  });
}

}  // namespace is_empty
