// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace reduce_empty {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace reduce_empty

// init.cpp
#include <reduce-empty/init.hpp>

namespace reduce_empty {

namespace core = temper::core;

static int32_t sum(std::shared_ptr<std::vector<int32_t>> const & values) {
  return core::List::reduce(values, [](int32_t a, int32_t b) -> int32_t { return core::Int::add(a, b); });
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, std::to_string(reduce_empty::sum(core::List::make<int32_t>(1, 2, 3))));
    core::Console::log(console, TEMPER_STR("before"));
    core::Console::log(console, std::to_string(reduce_empty::sum(core::List::make<int32_t>())));
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace reduce_empty
