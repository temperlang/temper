// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace read {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace read

// init.cpp
#include <read/init.hpp>

namespace read {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<core::Str> hay;

}  // namespace temper_globals

static void reportSliceInfo(core::Str const & s) {
  int32_t start = core::String::begin();
  int32_t end = core::String::end(s);
  int32_t count = core::String::countBetween(s, start, end);
  core::Console::log(temper_globals::console.value, "Count of code-points for `" + s + "`: " + std::to_string(count));
  core::Console::log(temper_globals::console.value, "Has at least 0: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 0)));
  core::Console::log(temper_globals::console.value, "Has at least 1: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 1)));
  core::Console::log(temper_globals::console.value, "Has at least 2: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 2)));
  core::Console::log(temper_globals::console.value, "Has at least 3: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 3)));
  core::Console::log(temper_globals::console.value, "Has at least 4: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 4)));
  core::Console::log(temper_globals::console.value, "Has at least 5: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 5)));
  core::Console::log(temper_globals::console.value, "Has at least 6: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 6)));
  core::Console::log(temper_globals::console.value, "Has at least 7: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 7)));
  core::Console::log(temper_globals::console.value, "Has at least 8: " + core::Boolean::toString(core::String::hasAtLeast(s, start, end, 8)));
  core::Console::log(temper_globals::console.value, TEMPER_STR("code-points"));
  {
    int32_t i = core::String::begin();
    while (core::String::hasIndex(s, i)) {
      int32_t cp = core::String::get(s, i);
      i = core::String::next(s, i);
      core::Console::log(temper_globals::console.value, "  " + core::Int::toString(cp, 16));
    }
  }
}

static void compareEnds(core::Str const & message) {
  int32_t start = core::String::begin();
  int32_t end = core::String::end(message);
  core::Console::log(temper_globals::console.value, "start < end? " + core::Boolean::toString(core::Compare::cmp(start, end) < 0));
  core::Console::log(temper_globals::console.value, "start > end? " + core::Boolean::toString(core::Compare::cmp(start, end) > 0));
  core::Console::log(temper_globals::console.value, "start == start? " + core::Boolean::toString(core::Compare::eq(start, start)));
  core::Console::log(temper_globals::console.value, "end < end? " + core::Boolean::toString(core::Compare::cmp(end, end) < 0));
  core::Console::log(temper_globals::console.value, "end <= end? " + core::Boolean::toString(core::Compare::cmp(end, end) <= 0));
}

static core::Expected<core::Str, std::shared_ptr<core::InvalidCodePoint>> fromCodeNoInline(int32_t code) {
  core::Console::log(temper_globals::console.value, "From 0x" + core::Int::toString(code, 16));
  return core::String::fromCodepoint(code);
}

static core::Expected<core::Str, std::shared_ptr<core::InvalidCodePoint>> fromCodesNoInline(std::shared_ptr<std::vector<int32_t>> const & codes) {
  core::Console::log(temper_globals::console.value, TEMPER_STR("From codes"));
  return core::String::fromCodepoints(codes);
}

static void checkParse(core::Str const & string, core::NullableParam<int32_t> radix) {
  core::Str converted;
  auto result = [&]() -> core::Expected<core::Str, std::shared_ptr<core::ParseError>> {
    int32_t int32;
    if (radix != nullptr) {
      auto Result = core::String::toInt32(string, radix.value);
      if (!Result.has_value()) {
        return core::unexpected(Result.error());
      }
      int32 = *Result;
    } else {
      auto Result2 = core::String::toInt32(string);
      if (!Result2.has_value()) {
        return core::unexpected(Result2.error());
      }
      int32 = *Result2;
    }
    return std::to_string(int32);
  }();
  if (result.has_value()) {
    converted = *result;
  } else {
    converted = TEMPER_STR("failed");
  }
  core::Console::log(temper_globals::console.value, "Parse " + string + " -> " + converted);
}

static int32_t reportFound(core::Str const & label, core::Str const & haystack, core::Str const & needle, core::NullableParam<int32_t> start) {
  int32_t start2 = start == nullptr ? core::String::begin() : start.value;
  int32_t index = core::String::indexOf(haystack, needle, start2);
  {
    if (core::String::isStringIndex(index)) {
      core::Str before = core::String::slice(haystack, core::String::prev(temper_globals::hay.value, start2), index);
      core::Console::log(temper_globals::console.value, label + ": " + before + "...");
      return core::String::next(haystack, index);
    } else {
      core::Console::log(temper_globals::console.value, label + ": (nope)");
      return core::String::begin();
    }
  }
}

static int32_t reportFound(core::Str const & label, core::Str const & haystack, core::Str const & needle) {
  return reportFound(label, haystack, needle, nullptr);
}

static int32_t foundAt(core::Str const & haystack, core::Str const & needle, core::NullableParam<int32_t> start) {
  int32_t start2 = start == nullptr ? core::String::begin() : start.value;
  int32_t index = core::String::indexOf(haystack, needle, start2);
  {
    if (core::String::isStringIndex(index)) {
      return core::String::countBetween(haystack, core::String::begin(), index);
    } else {
      return -1;
    }
  }
}

static int32_t foundAt(core::Str const & haystack, core::Str const & needle) {
  return foundAt(haystack, needle, nullptr);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::hay.construct();
    temper_globals::console.value = core::Console::get_console();
    read::reportSliceInfo(TEMPER_STR("κόσμε𐆊"));
    read::reportSliceInfo(TEMPER_STR("Hi"));
    read::compareEnds(TEMPER_STR("Hi"));
    auto Result = core::String::fromCodepoint(127757);
    if (!Result.has_value()) {
      core::uncaught_failure(Result.error());
    }
    core::Console::log(temper_globals::console.value, *Result);
    auto fromCodeNoInlineResult = fromCodeNoInline(127757);
    if (!fromCodeNoInlineResult.has_value()) {
      core::uncaught_failure(fromCodeNoInlineResult.error());
    }
    core::Console::log(temper_globals::console.value, *fromCodeNoInlineResult);
    core::Str value;
    auto fromCodeNoInlineResult2 = fromCodeNoInline(1114112);
    if (fromCodeNoInlineResult2.has_value()) {
      value = *fromCodeNoInlineResult2;
    } else {
      value = TEMPER_STR("nope");
    }
    core::Console::log(temper_globals::console.value, value);
    core::Str value2;
    auto fromCodeNoInlineResult3 = fromCodeNoInline(55296);
    if (fromCodeNoInlineResult3.has_value()) {
      value2 = *fromCodeNoInlineResult3;
    } else {
      value2 = TEMPER_STR("no to surrogate");
    }
    core::Console::log(temper_globals::console.value, value2);
    auto fromCodesNoInlineResult = read::fromCodesNoInline(core::List::make<int32_t>(128059, 8205, 10052, 65039));
    if (!fromCodesNoInlineResult.has_value()) {
      core::uncaught_failure(fromCodesNoInlineResult.error());
    }
    core::Console::log(temper_globals::console.value, *fromCodesNoInlineResult);
    core::Str value3;
    auto fromCodesNoInlineResult2 = read::fromCodesNoInline(core::List::make<int32_t>(55296, 56320));
    if (fromCodesNoInlineResult2.has_value()) {
      value3 = *fromCodesNoInlineResult2;
    } else {
      value3 = TEMPER_STR("no to surrogates");
    }
    core::Console::log(temper_globals::console.value, value3);
    core::Str value4;
    auto fromCodesNoInlineResult3 = read::fromCodesNoInline(core::List::make<int32_t>(65, -1, 56320));
    if (fromCodesNoInlineResult3.has_value()) {
      value4 = *fromCodesNoInlineResult3;
    } else {
      std::shared_ptr<core::InvalidCodePoint> e = fromCodesNoInlineResult3.error();
      value4 = core::Int::toString(e->codePoint, 16);
    }
    core::Console::log(temper_globals::console.value, value4);
    core::Str value5;
    auto Result2 = core::String::fromCodepoint(57343);
    if (Result2.has_value()) {
      value5 = *Result2;
    } else {
      std::shared_ptr<core::InvalidCodePoint> e2 = Result2.error();
      value5 = e2->get_message();
    }
    core::Console::log(temper_globals::console.value, value5);
    core::Str value6;
    auto Result3 = core::String::toInt32(TEMPER_STR("x"));
    if (Result3.has_value()) {
      value6 = std::to_string(*Result3);
    } else {
      std::shared_ptr<core::ParseError> e3 = Result3.error();
      value6 = e3->get_message();
    }
    core::Console::log(temper_globals::console.value, value6);
    core::Str value7;
    auto Result4 = core::Float64::toInt32(1e300);
    if (Result4.has_value()) {
      value7 = std::to_string(*Result4);
    } else {
      std::shared_ptr<core::ParseError> e4 = Result4.error();
      value7 = e4->reason;
    }
    core::Console::log(temper_globals::console.value, value7);
    int64_t value8;
    auto Result5 = core::String::toInt64(TEMPER_STR("12"));
    if (Result5.has_value()) {
      value8 = *Result5;
    } else {
      value8 = int64_t{-1};
    }
    core::Console::log(temper_globals::console.value, std::to_string(value8));
    read::checkParse(TEMPER_STR("-1"), nullptr);
    read::checkParse(TEMPER_STR("2"), nullptr);
    read::checkParse(TEMPER_STR(" 2 "), nullptr);
    read::checkParse(TEMPER_STR("2.0"), nullptr);
    read::checkParse(TEMPER_STR("2a"), nullptr);
    read::checkParse(TEMPER_STR("0x10"), nullptr);
    read::checkParse(TEMPER_STR("totally"), nullptr);
    read::checkParse(TEMPER_STR("2a"), 16);
    read::checkParse(TEMPER_STR("20"), 7);
    read::checkParse(TEMPER_STR("20"), 17);
    read::checkParse(TEMPER_STR("20"), 37);
    read::checkParse(TEMPER_STR("10"), 0);
    read::checkParse(TEMPER_STR("0"), 1);
    core::Str value9;
    auto Result6 = core::String::toInt64(TEMPER_STR("10"), 0);
    if (Result6.has_value()) {
      value9 = std::to_string(*Result6);
    } else {
      std::shared_ptr<core::ParseError> e5 = Result6.error();
      value9 = e5->reason;
    }
    core::Console::log(temper_globals::console.value, value9);
    int64_t value10;
    auto Result7 = core::String::toInt64(TEMPER_STR("10"), -2);
    if (Result7.has_value()) {
      value10 = *Result7;
    } else {
      value10 = int64_t{-1};
    }
    core::Console::log(temper_globals::console.value, std::to_string(value10));
    {
      core::Str s = TEMPER_STR("foo-bar");
      int32_t after1_2 = core::String::next(s, core::String::begin());
      int32_t after2_2 = core::String::next(s, after1_2);
      int32_t after3 = core::String::next(s, after2_2);
      int32_t after4 = core::String::next(s, after3);
      int32_t after4b = core::String::step(s, core::String::begin(), 4);
      int32_t before1 = core::String::prev(s, after4b);
      int32_t before3 = core::String::step(s, before1, -2);
      int32_t before3b = core::String::step(s, before3, 0);
      int32_t wayBefore = core::String::step(s, before3, -100);
      int32_t wayAfter = core::String::step(s, before3, 100);
      core::Console::log(temper_globals::console.value, "s[3:]  -> " + core::String::slice(s, after3, core::String::end(s)));
      core::Console::log(temper_globals::console.value, "s[:3]  -> " + core::String::slice(s, core::String::begin(), after3));
      core::Console::log(temper_globals::console.value, "s[1:4] -> " + core::String::slice(s, after1_2, after4));
      core::Console::log(temper_globals::console.value, "s[:4]  -> " + core::String::slice(s, core::String::begin(), after4));
      core::Console::log(temper_globals::console.value, "s[1:4] -> " + core::String::slice(s, before3b, after4b));
      core::Console::log(temper_globals::console.value, "s[-many:many] -> " + core::String::slice(s, wayBefore, wayAfter));
    }
    temper_globals::hay.value = TEMPER_STR("It was the best of times, it was the worst of times, ...");
    core::Str needle = TEMPER_STR("time");
    int32_t after1 = read::reportFound(TEMPER_STR("found 1"), temper_globals::hay.value, needle);
    int32_t after2 = read::reportFound(TEMPER_STR("found 2"), temper_globals::hay.value, needle, after1);
    read::reportFound(TEMPER_STR("found 3"), temper_globals::hay.value, needle, after2);
    read::reportFound(TEMPER_STR("hay 2"), TEMPER_STR("time comes for us all"), needle);
    read::reportFound(TEMPER_STR("hay 3"), TEMPER_STR("in the nick of time"), needle);
    read::reportFound(TEMPER_STR("hay 4"), TEMPER_STR("my name is tim"), needle);
    read::reportFound(TEMPER_STR("hay 5"), TEMPER_STR("tim"), needle);
    core::Str nulHay = TEMPER_STR("ab\000#\000", 5);
    core::Console::log(temper_globals::console.value, "nul: " + std::to_string(read::foundAt(nulHay, TEMPER_STR("\000", 1))));
    int32_t afterNul = core::String::next(nulHay, core::String::next(nulHay, core::String::next(nulHay, core::String::begin())));
    core::Console::log(temper_globals::console.value, "nul again: " + std::to_string(read::foundAt(nulHay, TEMPER_STR("\000", 1), afterNul)));
    core::Console::log(temper_globals::console.value, "soft hyphen: " + std::to_string(read::foundAt(TEMPER_STR("ab\u00AD#"), TEMPER_STR("\u00AD"))));
    core::Console::log(temper_globals::console.value, "past soft hyphen: " + std::to_string(read::foundAt(TEMPER_STR("ab\u00AD#"), TEMPER_STR("b#"))));
    core::Console::log(temper_globals::console.value, "zwj: " + std::to_string(read::foundAt(TEMPER_STR("a\u200Db"), TEMPER_STR("ab"))));
    core::Console::log(temper_globals::console.value, "composed: " + std::to_string(read::foundAt(TEMPER_STR("é"), TEMPER_STR("é"))));
  });
}

}  // namespace read
