// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace hiding {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace hiding

// init.cpp
#include <hiding/init.hpp>

#include <temper-core/boolean.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace hiding {

namespace core = temper::core;

static int32_t explicit_(int32_t int32_t_) {
  return core::Int::add(int32_t_, 1);
}

static int32_t core_(int32_t a, int32_t b) {
  return core::Int::div_wrap(a, b);
}

static bool before(bool a, bool b) {
  return a < b;
}

static core::Str shifts(int32_t n) {
  return std::to_string(core::Int::shl(1, n)) + " " + std::to_string(core::Int::shr(-8, n)) + " " + std::to_string(core::Int::ushr(-8, core::Int::sub(n, 4)));
}

static core::Str quoted(core::Str const & s) {
  return "[" + s + "]";
}

static core::Str swap(std::shared_ptr<std::vector<core::Str>> const & a, std::shared_ptr<std::vector<core::Str>> const & b) {
  return core::List::get<core::Str>(b, 0) + core::List::get<core::Str>(a, 0);
}

static core::Str move(std::shared_ptr<std::vector<core::Str>> const & xs) {
  return core::List::get<core::Str>(xs, 0);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Str std_ = TEMPER_STR("s");
    int32_t M_PI_ = 3;
    core::Console::log(console, std_ + " " + std::to_string(explicit_(1)) + " " + std::to_string(core_(7, 2)) + " " + std::to_string(M_PI_));
    core::Console::log(console, core::Boolean::toString(before(false, true)) + " " + core::Boolean::toString(TEMPER_STR("?\?=") < TEMPER_STR("?\?>")));
    core::Console::log(console, shifts(33));
    core::Console::log(console, core::Boolean::toString(TEMPER_STR("a\000b", 3) == TEMPER_STR("a\000b", 3)) + " " + core::Boolean::toString(TEMPER_STR("a\000b", 3) == TEMPER_STR("a\000c", 3)));
    std::shared_ptr<std::vector<core::Str>> xs = core::List::make<core::Str>(TEMPER_STR("x"));
    std::shared_ptr<std::vector<core::Str>> ys = core::List::make<core::Str>(TEMPER_STR("y"));
    core::Console::log(console, hiding::quoted(std_) + " " + hiding::swap(xs, ys) + " " + hiding::move(xs));
  });
}

}  // namespace hiding
