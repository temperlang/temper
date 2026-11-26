// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/collections.hpp>

namespace queue_empty {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace queue_empty

// init.cpp
#include <queue-empty/init.hpp>

namespace queue_empty {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    temper_std::global_init_collections();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::deque<core::Str>> queue = core::Queue::make<core::Str>();
    core::Queue::add<core::Str>(queue, TEMPER_STR("a"));
    core::Console::log(console, core::Queue::removeFirst<core::Str>(queue));
    core::Console::log(console, TEMPER_STR("before"));
    core::Console::log(console, core::Queue::removeFirst<core::Str>(queue));
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace queue_empty
