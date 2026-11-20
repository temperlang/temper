// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace shifty {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace shifty

// init.cpp
#include <shifty/init.hpp>

#include <temper-core/compare.hpp>
#include <temper-core/int.hpp>
#include <temper-core/int64.hpp>
#include <temper-core/console.hpp>
#include <temper-core/string.hpp>
#include <temper-core/string_builder.hpp>

namespace shifty {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static core::Str insertUnderscores(core::Str const & hexString) {
  std::shared_ptr<std::ostringstream> sb = core::StringBuilder::make();
  int32_t idx = core::String::begin();
  int32_t end = core::String::end(hexString);
  if (core::Compare::cmp(idx, end) < 0) {
    int32_t possibleSign = core::String::get(hexString, idx);
    if (possibleSign == 43 || possibleSign == 45) {
      idx = core::String::next(hexString, idx);
      core::StringBuilder::appendBetween(sb, hexString, core::String::begin(), idx);
    }
  }
  int32_t nFirstGroup = core::String::countBetween(hexString, idx, end) % 4;
  bool needsUnderscore = false;
  if (nFirstGroup != 0) {
    int32_t groupStart = idx;
    {
      int32_t i = 0;
      while (i < nFirstGroup) {
        idx = core::String::next(hexString, idx);
        i = core::Int::add(i, 1);
      }
    }
    core::StringBuilder::appendBetween(sb, hexString, groupStart, idx);
    needsUnderscore = true;
  }
  while (core::Compare::cmp(idx, end) < 0) {
    int32_t groupStart = idx;
    idx = core::String::step(hexString, idx, 4);
    if (needsUnderscore) {
      core::StringBuilder::append(sb, TEMPER_STR("_"));
    }
    core::StringBuilder::appendBetween(sb, hexString, groupStart, idx);
    needsUnderscore = true;
  }
  return core::StringBuilder::toString(sb);
}

static core::Str fmt32(int32_t n) {
  return shifty::insertUnderscores(core::Int::toString(n, 16));
}

static core::Str fmt64(int64_t n) {
  return shifty::insertUnderscores(core::Int64::toString(n, 16));
}

static void and32(int32_t a, int32_t b) {
  int32_t c = a & b;
  core::Str string = fmt32(a);
  core::Str value = fmt32(b);
  core::Console::log(temper_globals::console.value, "0x:" + string + " & " + value + " == " + fmt32(c));
}

static void and64(int64_t a, int64_t b) {
  int64_t c = a & b;
  core::Str string = fmt64(a);
  core::Str value = fmt64(b);
  core::Console::log(temper_globals::console.value, "0x:" + string + " & " + value + " == " + fmt64(c));
}

static void or32(int32_t a, int32_t b) {
  int32_t c = a | b;
  core::Str string = fmt32(a);
  core::Str value = fmt32(b);
  core::Console::log(temper_globals::console.value, "0x:" + string + " | " + value + " == " + fmt32(c));
}

static void or64(int64_t a, int64_t b) {
  int64_t c = a | b;
  core::Str string = fmt64(a);
  core::Str value = fmt64(b);
  core::Console::log(temper_globals::console.value, "0x:" + string + " | " + value + " == " + fmt64(c));
}

static void bComp32(int32_t n) {
  core::Str string = fmt32(n);
  core::Console::log(temper_globals::console.value, "0x:~" + string + " == " + fmt32(~n));
}

static void bComp64(int64_t n) {
  core::Str string = fmt64(n);
  core::Console::log(temper_globals::console.value, "0x:~" + string + " == " + fmt64(~n));
}

static void shl32(int32_t a, int32_t b) {
  int32_t c = core::Int::shl(a, b);
  core::Str string = fmt32(a);
  core::Console::log(temper_globals::console.value, "0x:" + string + " << " + std::to_string(b) + " == " + fmt32(c));
}

static void shl64(int64_t a, int32_t b) {
  int64_t c = core::Int64::shl(a, b);
  core::Str string = fmt64(a);
  core::Console::log(temper_globals::console.value, "0x:" + string + " << " + std::to_string(b) + " == " + fmt64(c));
}

static void shr32(int32_t a, int32_t b) {
  int32_t c = core::Int::shr(a, b);
  core::Str string = fmt32(a);
  core::Console::log(temper_globals::console.value, "0x:" + string + " >> " + std::to_string(b) + " == " + fmt32(c));
}

static void shr64(int64_t a, int32_t b) {
  int64_t c = core::Int64::shr(a, b);
  core::Str string = fmt64(a);
  core::Console::log(temper_globals::console.value, "0x:" + string + " >> " + std::to_string(b) + " == " + fmt64(c));
}

static void uShr32(int32_t a, int32_t b) {
  int32_t c = core::Int::ushr(a, b);
  core::Str string = fmt32(a);
  core::Console::log(temper_globals::console.value, "0x:" + string + " >>> " + std::to_string(b) + " == " + fmt32(c));
}

static void uShr64(int64_t a, int32_t b) {
  int64_t c = core::Int64::ushr(a, b);
  core::Str string = fmt64(a);
  core::Console::log(temper_globals::console.value, "0x:" + string + " >>> " + std::to_string(b) + " == " + fmt64(c));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    core::Console::log(temper_globals::console.value, TEMPER_STR("and32"));
    and32(0, 0);
    and32(3, 1);
    and32(2, 3);
    and32(-2, 7);
    and32(-809434545, 901070595);
    core::Console::log(temper_globals::console.value, TEMPER_STR("and64"));
    and64(int64_t{0}, int64_t{0});
    and64(int64_t{3}, int64_t{1});
    and64(int64_t{2}, int64_t{3});
    and64(int64_t{-2}, int64_t{7});
    and64(int64_t{297421889964898413}, int64_t{-3505189831424144594});
    core::Console::log(temper_globals::console.value, TEMPER_STR("or32"));
    or32(0, 0);
    or32(0, 1);
    or32(2, 1);
    or32(2, 2);
    or32(3, 1);
    or32(2, 3);
    or32(-2, 7);
    or32(-809434545, 901070595);
    core::Console::log(temper_globals::console.value, TEMPER_STR("or64"));
    or64(int64_t{0}, int64_t{0});
    or64(int64_t{0}, int64_t{1});
    or64(int64_t{2}, int64_t{1});
    or64(int64_t{2}, int64_t{2});
    or64(int64_t{3}, int64_t{1});
    or64(int64_t{2}, int64_t{3});
    or64(int64_t{-2}, int64_t{7});
    or64(int64_t{297421889964898413}, int64_t{-3505189831424144594});
    core::Console::log(temper_globals::console.value, TEMPER_STR("bComp32"));
    bComp32(0);
    bComp32(1);
    bComp32(-1);
    bComp32(2);
    bComp32(-2);
    bComp32(-809434545);
    bComp32(901070595);
    core::Console::log(temper_globals::console.value, TEMPER_STR("bComp64"));
    bComp64(int64_t{0});
    bComp64(int64_t{1});
    bComp64(int64_t{-1});
    bComp64(int64_t{2});
    bComp64(int64_t{-2});
    bComp64(int64_t{297421889964898413});
    bComp64(int64_t{-3505189831424144594});
    core::Console::log(temper_globals::console.value, TEMPER_STR("shl32"));
    shl32(5, 0);
    shl32(5, 1);
    shl32(5, 2);
    shl32(5, 3);
    shl32(5, 4);
    shl32(5, 5);
    shl32(5, 20);
    shl32(5, 28);
    shl32(5, 29);
    shl32(5, 30);
    shl32(5, 31);
    shl32(5, 32);
    shl32(5, 33);
    shl32(5, -1);
    shl32(5, -30);
    shl32(-1, 0);
    shl32(-1, 1);
    shl32(-1, 2);
    core::Console::log(temper_globals::console.value, TEMPER_STR("shl64"));
    shl64(int64_t{5}, 0);
    shl64(int64_t{5}, 1);
    shl64(int64_t{5}, 2);
    shl64(int64_t{5}, 3);
    shl64(int64_t{5}, 4);
    shl64(int64_t{5}, 5);
    shl64(int64_t{5}, 33);
    shl64(int64_t{5}, 62);
    shl64(int64_t{5}, 63);
    shl64(int64_t{5}, 64);
    shl64(int64_t{5}, -1);
    shl64(int64_t{5}, -60);
    shl64(int64_t{-1}, 0);
    shl64(int64_t{-1}, 1);
    shl64(int64_t{-1}, 2);
    core::Console::log(temper_globals::console.value, TEMPER_STR("shr32"));
    shr32(128, 0);
    shr32(128, 1);
    shr32(128, 2);
    shr32(128, 3);
    shr32(128, 4);
    shr32(128, 5);
    shr32(128, 6);
    shr32(128, 7);
    shr32(128, 8);
    shr32(128, 9);
    shr32(128, 31);
    shr32(128, 32);
    shr32(128, 33);
    shr32(128, -1);
    shr32(128, -30);
    shr32(-128, 0);
    shr32(-128, 1);
    shr32(-128, 2);
    shr32(-128, 3);
    shr32(-128, 4);
    core::Console::log(temper_globals::console.value, TEMPER_STR("shr64"));
    shr64(int64_t{128}, 0);
    shr64(int64_t{128}, 1);
    shr64(int64_t{128}, 2);
    shr64(int64_t{128}, 3);
    shr64(int64_t{128}, 4);
    shr64(int64_t{128}, 5);
    shr64(int64_t{128}, 6);
    shr64(int64_t{128}, 7);
    shr64(int64_t{128}, 8);
    shr64(int64_t{128}, 9);
    shr64(int64_t{128}, 32);
    shr64(int64_t{128}, 63);
    shr64(int64_t{128}, 64);
    shr64(int64_t{128}, 65);
    shr64(int64_t{128}, -1);
    shr64(int64_t{128}, -62);
    shr64(int64_t{-128}, 0);
    shr64(int64_t{-128}, 1);
    shr64(int64_t{-128}, 2);
    shr64(int64_t{-128}, 3);
    shr64(int64_t{-128}, 4);
    core::Console::log(temper_globals::console.value, TEMPER_STR("uShr32"));
    uShr32(128, 0);
    uShr32(128, 1);
    uShr32(128, 2);
    uShr32(128, 3);
    uShr32(128, 4);
    uShr32(128, 5);
    uShr32(128, 6);
    uShr32(128, 7);
    uShr32(128, 8);
    uShr32(128, 9);
    uShr32(128, 31);
    uShr32(128, 32);
    uShr32(128, 33);
    uShr32(128, -1);
    uShr32(128, -30);
    uShr32(-128, 0);
    uShr32(-128, 1);
    uShr32(-128, 2);
    uShr32(-128, 3);
    uShr32(-128, 4);
    core::Console::log(temper_globals::console.value, TEMPER_STR("uShr64"));
    uShr64(int64_t{128}, 0);
    uShr64(int64_t{128}, 1);
    uShr64(int64_t{128}, 2);
    uShr64(int64_t{128}, 3);
    uShr64(int64_t{128}, 4);
    uShr64(int64_t{128}, 5);
    uShr64(int64_t{128}, 6);
    uShr64(int64_t{128}, 7);
    uShr64(int64_t{128}, 8);
    uShr64(int64_t{128}, 9);
    uShr64(int64_t{128}, 32);
    uShr64(int64_t{128}, 63);
    uShr64(int64_t{128}, 64);
    uShr64(int64_t{128}, 65);
    uShr64(int64_t{128}, -1);
    uShr64(int64_t{128}, -62);
    uShr64(int64_t{-128}, 0);
    uShr64(int64_t{-128}, 1);
    uShr64(int64_t{-128}, 2);
    uShr64(int64_t{-128}, 3);
    uShr64(int64_t{-128}, 4);
  });
}

}  // namespace shifty
