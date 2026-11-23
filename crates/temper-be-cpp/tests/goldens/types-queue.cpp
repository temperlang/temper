// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/collections.hpp>

namespace queue {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace queue

// init.cpp
#include <queue/init.hpp>

namespace queue {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<std::shared_ptr<std::deque<core::Str>>> queue;

}  // namespace temper_globals

static void checkEmpty() {
  core::Console::log(temper_globals::console.value, core::Queue::isEmpty<core::Str>(temper_globals::queue.value) ? TEMPER_STR("Empty") : TEMPER_STR("Not empty"));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::queue.construct();
    temper_std::global_init_collections();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::queue.value = core::Queue::make<core::Str>();
    checkEmpty();
    if (!core::Queue::isEmpty<core::Str>(temper_globals::queue.value)) {
      core::Console::log(temper_globals::console.value, core::Queue::removeFirst<core::Str>(temper_globals::queue.value));
    }
    core::Queue::add<core::Str>(temper_globals::queue.value, TEMPER_STR("foo"));
    core::Queue::add<core::Str>(temper_globals::queue.value, TEMPER_STR("bar"));
    core::Console::log(temper_globals::console.value, core::Queue::removeFirst<core::Str>(temper_globals::queue.value));
    core::Queue::add<core::Str>(temper_globals::queue.value, TEMPER_STR("baz"));
    core::Console::log(temper_globals::console.value, core::Queue::removeFirst<core::Str>(temper_globals::queue.value));
    core::Queue::add<core::Str>(temper_globals::queue.value, TEMPER_STR("boo"));
    checkEmpty();
    while (!core::Queue::isEmpty<core::Str>(temper_globals::queue.value)) {
      core::Console::log(temper_globals::console.value, core::Queue::removeFirst<core::Str>(temper_globals::queue.value));
    }
    checkEmpty();
    std::shared_ptr<std::deque<core::NullableParam<int32_t>>> numbers = core::Queue::make<core::NullableParam<int32_t>>();
    int32_t sum = 0;
    {
      int32_t i = 0;
      while (i < 100) {
        core::Queue::add<core::NullableParam<int32_t>>(numbers, i);
        core::Queue::add<core::NullableParam<int32_t>>(numbers, nullptr);
        if (i % 3 == 0) {
          int32_t int32 = sum;
          core::NullableParam<int32_t> subject = core::Queue::removeFirst<core::NullableParam<int32_t>>(numbers);
          sum = core::Int::add(int32, subject == nullptr ? -1000 : subject.value);
          int32_t value = sum;
          core::NullableParam<int32_t> subject2 = core::Queue::removeFirst<core::NullableParam<int32_t>>(numbers);
          sum = core::Int::add(value, subject2 == nullptr ? 1 : subject2.value);
        }
        i = core::Int::add(i, 1);
      }
    }
    bool order = true;
    int32_t expected = 34;
    while (!core::Queue::isEmpty<core::NullableParam<int32_t>>(numbers)) {
      core::NullableParam<int32_t> n = core::Queue::removeFirst<core::NullableParam<int32_t>>(numbers);
      if (n != nullptr) {
        if (n.value != expected) {
          order = false;
        }
        expected = core::Int::add(expected, 1);
      }
    }
    core::Console::log(temper_globals::console.value, "sum " + std::to_string(sum) + ", in order " + core::Boolean::toString(order) + ", through " + std::to_string(expected));
  });
}

}  // namespace queue
