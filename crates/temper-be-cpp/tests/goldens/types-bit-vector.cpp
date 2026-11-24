// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/collections.hpp>

namespace bit_vector {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace bit_vector

// init.cpp
#include <bit-vector/init.hpp>

namespace bit_vector {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    temper_std::global_init_collections();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<bool>> v = core::BitVector::make(16);
    core::Console::log(console, "v[2]=" + core::Boolean::toString(core::BitVector::get(v, 2)));
    core::Console::log(console, "v[3]=" + core::Boolean::toString(core::BitVector::get(v, 3)));
    core::Console::log(console, TEMPER_STR("v[3] <- true"));
    core::BitVector::set(v, 3, true);
    core::Console::log(console, "v[2]=" + core::Boolean::toString(core::BitVector::get(v, 2)));
    core::Console::log(console, "v[3]=" + core::Boolean::toString(core::BitVector::get(v, 3)));
    core::Console::log(console, TEMPER_STR("v[3] <- false"));
    core::BitVector::set(v, 3, false);
    core::Console::log(console, "v[2]=" + core::Boolean::toString(core::BitVector::get(v, 2)));
    core::Console::log(console, "v[3]=" + core::Boolean::toString(core::BitVector::get(v, 3)));
    core::BitVector::set(v, 100, true);
    core::BitVector::set(v, 200, false);
    core::Console::log(console, "v[99]=" + core::Boolean::toString(core::BitVector::get(v, 99)) + " v[100]=" + core::Boolean::toString(core::BitVector::get(v, 100)) + " v[101]=" + core::Boolean::toString(core::BitVector::get(v, 101)) + " v[200]=" + core::Boolean::toString(core::BitVector::get(v, 200)) + " v[-1]=" + core::Boolean::toString(core::BitVector::get(v, -1)));
    core::BitVector::set(v, 1000, true);
    core::Console::log(console, "v[999]=" + core::Boolean::toString(core::BitVector::get(v, 999)) + " v[1000]=" + core::Boolean::toString(core::BitVector::get(v, 1000)) + " v[100]=" + core::Boolean::toString(core::BitVector::get(v, 100)));
    std::shared_ptr<std::vector<bool>> u = core::BitVector::make(0);
    core::BitVector::set(u, 8, true);
    core::BitVector::set(u, 32, true);
    core::BitVector::set(u, 64, true);
    core::Console::log(console, core::Boolean::toString(core::BitVector::get(u, 7)) + " " + core::Boolean::toString(core::BitVector::get(u, 8)) + " " + core::Boolean::toString(core::BitVector::get(u, 9)) + " " + core::Boolean::toString(core::BitVector::get(u, 31)) + " " + core::Boolean::toString(core::BitVector::get(u, 32)) + " " + core::Boolean::toString(core::BitVector::get(u, 33)) + " " + core::Boolean::toString(core::BitVector::get(u, 63)) + " " + core::Boolean::toString(core::BitVector::get(u, 64)) + " " + core::Boolean::toString(core::BitVector::get(u, 65)));
    core::BitVector::set(v, 1000, false);
    core::Console::log(console, "v[1000]=" + core::Boolean::toString(core::BitVector::get(v, 1000)) + " v[999]=" + core::Boolean::toString(core::BitVector::get(v, 999)));
    std::shared_ptr<std::vector<bool>> w = core::BitVector::make(-9);
    core::BitVector::set(w, 1, true);
    core::Console::log(console, "w[0]=" + core::Boolean::toString(core::BitVector::get(w, 0)) + " w[1]=" + core::Boolean::toString(core::BitVector::get(w, 1)));
    core::Console::log(console, TEMPER_STR("before"));
    core::BitVector::set(v, -1, true);
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace bit_vector
