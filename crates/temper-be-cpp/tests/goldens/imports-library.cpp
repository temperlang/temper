// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <modules/core.hpp>
#include <renamed/init.hpp>
#include <modules/init.hpp>
#include <modules/helpers.hpp>

namespace imports_library {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

int32_t both(int32_t n);

}  // namespace imports_library

// init.cpp
#include <imports-library/init.hpp>

namespace imports_library {

namespace core = temper::core;

int32_t both(int32_t n) {
  global_init_init();
  int32_t int32 = modules::sum(n);
  return core::Int::add(core::Int::add(int32, modules::twice(n)), modules::temper_globals::base.value);
}

void global_init_init() {
  core::init_once([] {
    modules::global_init_core();
    renamed::global_init_init();
    modules::global_init_init();
    modules::global_init_helpers();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, "both " + std::to_string(both(2)));
    std::shared_ptr<modules::Ratio> third = modules::Ratio::make(1, 3);
    core::Str string = "ratio " + core::Boolean::toString(third->eq(modules::Ratio::make(3, 9))) + " ";
    core::Str value = string;
    std::shared_ptr<modules::Ratio> left = modules::ratioOf(2, 6);
    bool boolean = left == nullptr ? false : left->eq(third);
    std::shared_ptr<modules::Ratio> left2 = modules::ratioOf(1, 0);
    core::Console::log(console, value + core::Boolean::toString(boolean) + " " + core::Boolean::toString(left2 == nullptr ? false : left2->eq(third)));
    std::shared_ptr<renamed::Checked> checked = renamed::Checked::make(renamed::lambda(renamed::temper_globals::native.value));
    int32_t int32 = checked->delete_();
    core::Console::log(console, "renamed " + std::to_string(int32) + " " + std::to_string(checked->get_local()) + " " + std::to_string(checked->end));
    std::shared_ptr<renamed::Builtins> b = renamed::Builtins::make(4);
    int32_t value2 = b->get(1);
    int32_t value3 = b->set(3);
    int32_t value4 = b->map(5);
    core::Str value5 = b->format();
    int32_t value6 = b->get_int_();
    core::Console::log(console, "builtins " + std::to_string(b->hash) + " " + std::to_string(value2) + " " + std::to_string(value3) + " " + std::to_string(value4) + " " + value5 + " " + std::to_string(value6) + " " + std::to_string(b->later()));
    core::Console::log(console, b->print());
  });
}

}  // namespace imports_library
