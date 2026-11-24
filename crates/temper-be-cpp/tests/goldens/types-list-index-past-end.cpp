// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace index_past_end {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace index_past_end

// init.cpp
#include <index-past-end/init.hpp>

namespace index_past_end {

namespace core = temper::core;

static core::Str at(std::shared_ptr<std::vector<core::Str>> const & listed, int32_t i) {
  return core::List::get<core::Str>(listed, i);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<core::Str>> letters = core::List::make<core::Str>(TEMPER_STR("a"), TEMPER_STR("b"), TEMPER_STR("c"));
    core::Console::log(console, index_past_end::at(letters, 2));
    core::Console::log(console, TEMPER_STR("before"));
    core::Console::log(console, index_past_end::at(letters, 3));
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace index_past_end
