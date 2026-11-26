// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace builder_set_negative {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace builder_set_negative

// init.cpp
#include <builder-set-negative/init.hpp>

namespace builder_set_negative {

namespace core = temper::core;

static void put(std::shared_ptr<std::vector<int32_t>> const & builder, int32_t i, int32_t value) {
  core::ListBuilder::set<int32_t>(builder, i, value);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<int32_t>> builder = core::ListBuilder::make<int32_t>();
    core::ListBuilder::add<int32_t>(builder, 1);
    core::ListBuilder::add<int32_t>(builder, 2);
    builder_set_negative::put(builder, 1, 5);
    core::Console::log(console, core::List::join(builder, TEMPER_STR(", "), [](int32_t n) -> core::Str { return std::to_string(n); }));
    core::Console::log(console, TEMPER_STR("before"));
    builder_set_negative::put(builder, -1, 0);
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace builder_set_negative
