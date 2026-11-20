// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace deque {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace deque

// init.cpp
#include <deque/init.hpp>

#include <temper-core/console.hpp>
#include <temper-core/deque.hpp>
#include <deque>

namespace deque {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<std::shared_ptr<std::deque<core::Str>>> deque;

}  // namespace temper_globals

static void checkEmpty() {
  core::Console::log(temper_globals::console.value, core::Deque::isEmpty<core::Str>(temper_globals::deque.value) ? TEMPER_STR("Empty") : TEMPER_STR("Not empty"));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::deque.construct();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::deque.value = core::Deque::make<core::Str>();
    checkEmpty();
    if (!core::Deque::isEmpty<core::Str>(temper_globals::deque.value)) {
      core::Console::log(temper_globals::console.value, core::Deque::removeFirst<core::Str>(temper_globals::deque.value));
    }
    core::Deque::add<core::Str>(temper_globals::deque.value, TEMPER_STR("foo"));
    core::Deque::add<core::Str>(temper_globals::deque.value, TEMPER_STR("bar"));
    core::Console::log(temper_globals::console.value, core::Deque::removeFirst<core::Str>(temper_globals::deque.value));
    core::Deque::add<core::Str>(temper_globals::deque.value, TEMPER_STR("baz"));
    core::Console::log(temper_globals::console.value, core::Deque::removeFirst<core::Str>(temper_globals::deque.value));
    core::Deque::add<core::Str>(temper_globals::deque.value, TEMPER_STR("boo"));
    checkEmpty();
    while (!core::Deque::isEmpty<core::Str>(temper_globals::deque.value)) {
      core::Console::log(temper_globals::console.value, core::Deque::removeFirst<core::Str>(temper_globals::deque.value));
    }
    checkEmpty();
  });
}

}  // namespace deque
