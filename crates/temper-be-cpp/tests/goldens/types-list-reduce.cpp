// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace reduce {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace reduce

// init.cpp
#include <reduce/init.hpp>

#include <temper-core/boolean.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace reduce {

namespace core = temper::core;

template <class T>
static bool all(std::shared_ptr<std::vector<T>> const & items, std::function<bool(T)> const & check) {
  return core::List::reduceFrom(items, true, [=](bool result, T it) -> bool { if (result) { return check(it); } else { return false; } });
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<int32_t>> vals = core::List::make<int32_t>(2, 3, 4);
    int32_t sum = core::List::reduce(vals, [](int32_t sum2, int32_t n) -> int32_t { return core::Int::add(sum2, n); });
    core::Str poorReverseJoin = core::List::reduceFrom(vals, core::Str(), [](core::Str text, int32_t n) -> core::Str { return std::to_string(n) + text; });
    core::Console::log(console, "Reductions: " + std::to_string(sum) + " " + poorReverseJoin);
    core::Console::log(console, "All even: " + core::Boolean::toString(reduce::all<int32_t>(vals, [](int32_t n) -> bool { return n % 2 == 0; })));
    core::Console::log(console, "All positive: " + core::Boolean::toString(reduce::all<int32_t>(vals, [](int32_t n) -> bool { return n > 0; })));
  });
}

}  // namespace reduce
