// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace remove_last_empty {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace remove_last_empty

// init.cpp
#include <remove-last-empty/init.hpp>

namespace remove_last_empty {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<core::Str>> builder = core::ListBuilder::make<core::Str>();
    core::ListBuilder::add<core::Str>(builder, TEMPER_STR("a"));
    core::Console::log(console, core::ListBuilder::removeLast<core::Str>(builder));
    core::Console::log(console, TEMPER_STR("before"));
    core::Console::log(console, core::ListBuilder::removeLast<core::Str>(builder));
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace remove_last_empty
