// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace index_at_end {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace index_at_end

// init.cpp
#include <index-at-end/init.hpp>

namespace index_at_end {

namespace core = temper::core;

static int32_t codeAt(core::Str const & s, int32_t i) {
  return core::String::get(s, i);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Str text = TEMPER_STR("abc");
    core::Console::log(console, std::to_string(index_at_end::codeAt(text, core::String::begin())));
    core::Console::log(console, TEMPER_STR("before"));
    core::Console::log(console, std::to_string(index_at_end::codeAt(text, core::String::end(text))));
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace index_at_end
