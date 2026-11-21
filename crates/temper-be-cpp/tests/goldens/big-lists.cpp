// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace big_lists {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace big_lists

// init.cpp
#include <big-lists/init.hpp>

namespace big_lists {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t n = 200000;
    std::shared_ptr<std::vector<int32_t>> source = core::ListBuilder::make<int32_t>();
    {
      int32_t i = 0;
      while (i < n) {
        core::ListBuilder::add<int32_t>(source, i);
        i = core::Int::add(i, 1);
      }
    }
    std::shared_ptr<std::vector<int32_t>> values = core::List::toList<int32_t>(source);
    std::shared_ptr<std::vector<int32_t>> b = core::ListBuilder::make<int32_t>();
    core::ListBuilder::addAll<int32_t>(b, values);
    core::ListBuilder::addAll<int32_t>(b, values, 0);
    core::ListBuilder::add<int32_t>(b, -1, n);
    core::ListBuilder::addAll<int32_t>(b, values, n);
    core::Console::log(console, std::to_string(core::List::length<int32_t>(b)) + " " + std::to_string(core::List::get<int32_t>(b, 0)) + " " + std::to_string(core::List::get<int32_t>(b, core::Int::sub(n, 1))) + " " + std::to_string(core::List::get<int32_t>(b, n)) + " " + std::to_string(core::List::get<int32_t>(b, core::Int::mul(2, n))) + " " + std::to_string(core::List::get<int32_t>(b, core::Int::add(core::Int::mul(2, n), 1))) + " " + std::to_string(core::List::get<int32_t>(b, core::Int::mul(3, n))));
    std::shared_ptr<std::vector<int32_t>> twice = core::ListBuilder::make<int32_t>();
    core::ListBuilder::addAll<int32_t>(twice, values);
    core::ListBuilder::addAll<int32_t>(twice, twice, 1);
    core::Console::log(console, std::to_string(core::List::length<int32_t>(twice)) + " " + std::to_string(core::List::get<int32_t>(twice, 0)) + " " + std::to_string(core::List::get<int32_t>(twice, 1)) + " " + std::to_string(core::List::get<int32_t>(twice, n)) + " " + std::to_string(core::List::get<int32_t>(twice, core::Int::add(n, 1))));
    std::shared_ptr<std::vector<int32_t>> s = core::ListBuilder::make<int32_t>();
    core::ListBuilder::add<int32_t>(s, 7);
    core::ListBuilder::add<int32_t>(s, 8);
    std::shared_ptr<std::vector<int32_t>> removed = core::ListBuilder::splice<int32_t>(s, 1, 0, values);
    core::Console::log(console, std::to_string(core::List::length<int32_t>(s)) + " " + std::to_string(core::List::length<int32_t>(removed)) + " " + std::to_string(core::List::get<int32_t>(s, 0)) + " " + std::to_string(core::List::get<int32_t>(s, 1)) + " " + std::to_string(core::List::get<int32_t>(s, n)) + " " + std::to_string(core::List::get<int32_t>(s, core::Int::add(n, 1))));
    std::shared_ptr<std::vector<int32_t>> c = core::ListBuilder::make<int32_t>();
    core::ListBuilder::add<int32_t>(c, 7);
    core::ListBuilder::add<int32_t>(c, 8);
    core::ListBuilder::splice<int32_t>(c, -1, 0, values);
    core::ListBuilder::splice<int32_t>(c, core::Int::mul(10, n), 0, values);
    core::Console::log(console, std::to_string(core::List::length<int32_t>(c)) + " " + std::to_string(core::List::get<int32_t>(c, 0)) + " " + std::to_string(core::List::get<int32_t>(c, core::Int::sub(n, 1))) + " " + std::to_string(core::List::get<int32_t>(c, n)) + " " + std::to_string(core::List::get<int32_t>(c, core::Int::add(n, 1))) + " " + std::to_string(core::List::get<int32_t>(c, core::Int::add(n, 2))) + " " + std::to_string(core::List::get<int32_t>(c, core::Int::add(core::Int::mul(2, n), 1))));
    std::shared_ptr<std::vector<int32_t>> own = core::ListBuilder::make<int32_t>();
    core::ListBuilder::addAll<int32_t>(own, values);
    std::shared_ptr<std::vector<int32_t>> gone = core::ListBuilder::splice<int32_t>(own, 0, 1, own);
    core::Console::log(console, std::to_string(core::List::length<int32_t>(own)) + " " + std::to_string(core::List::length<int32_t>(gone)) + " " + std::to_string(core::List::get<int32_t>(own, 0)) + " " + std::to_string(core::List::get<int32_t>(own, core::Int::sub(n, 1))) + " " + std::to_string(core::List::get<int32_t>(own, n)) + " " + std::to_string(core::List::get<int32_t>(own, core::Int::sub(core::Int::mul(2, n), 2))));
    std::shared_ptr<std::vector<int32_t>> kept = core::List::filter(values, [](int32_t x) -> bool { return x != 5; });
    core::Console::log(console, std::to_string(core::List::length<int32_t>(kept)) + " " + std::to_string(core::List::get<int32_t>(kept, 4)) + " " + std::to_string(core::List::get<int32_t>(kept, 5)) + " " + std::to_string(core::List::get<int32_t>(kept, core::Int::sub(n, 2))));
    std::shared_ptr<std::vector<int32_t>> codes = core::ListBuilder::make<int32_t>();
    {
      int32_t i2 = 0;
      while (i2 < n) {
        core::ListBuilder::add<int32_t>(codes, core::Int::add(97, i2 % 3));
        i2 = core::Int::add(i2, 1);
      }
    }
    core::Str text;
    try {
      text = core::String::fromCodepoints(core::List::toList<int32_t>(codes));
    } catch (core::TemperBubble const &) {
      text = core::Str();
    }
    int32_t four = core::String::next(text, core::String::next(text, core::String::next(text, core::String::next(text, core::String::begin()))));
    core::Console::log(console, std::to_string(core::String::countBetween(text, core::String::begin(), core::String::end(text))) + " " + core::String::slice(text, core::String::begin(), four));
  });
}

}  // namespace big_lists
