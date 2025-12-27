#ifndef TEMPER_CORE_INT_HPP
#define TEMPER_CORE_INT_HPP

#include <cctype>
#include <cerrno>
#include <stdint.h>
#include <stdlib.h>
#include "expected.hpp"
#include "shared.hpp"

namespace temper {
namespace core {

// Explicitly support twos-complement signed wrapping arithmetic in c++03.

const int32_t int32_max = 0x7FFFFFFF;
const int32_t int32_min = -0x7FFFFFFF - 1;
const uint32_t uint32_max = 0xFFFFFFFFu;
const uint32_t uint32_half = 0x80000000u;

const int64_t int64_max = 0x7FFFFFFFFFFFFFFF;
const int64_t int64_min = -0x7FFFFFFFFFFFFFFF - 1;
const uint64_t uint64_max = 0xFFFFFFFFFFFFFFFFu;
const uint64_t uint64_half = 0x8000000000000000u;

template<typename T>
struct Limits;

template<>
struct Limits<int32_t> {
  static const int32_t max = int32_max;
  static const int32_t min = int32_min;
};

template<>
struct Limits<int64_t> {
  static const int64_t max = int64_max;
  static const int64_t min = int64_min;
};

// Unsigned to signed is implementation-defined, if I read correctly.

int32_t to_signed(uint32_t i) {
  return i < uint32_half ? int32_t(i) : int32_t(i - uint32_half) + int32_min;
}

int64_t to_signed(uint64_t i) {
  return i < uint64_half ? int64_t(i) : int64_t(i - uint64_half) + int64_min;
}

// Signed to unsigned respects modulo arithmetic, if I read correctly.

uint32_t to_unsigned(int32_t i) {
  return uint32_t(i);
}

uint64_t to_unsigned(int64_t i) {
  return uint64_t(i);
}

// Given above definitions, support both int32_t and int64_t.

template<typename T>
T add(T i, T j) {
  return to_signed(to_unsigned(i) + to_unsigned(j));
}

template<typename T>
T div(T i, T j) {
  if (j == T(-1) && i == Limits<T>::min) {
    // The one risk of overflow for signed division.
    return Limits<T>::min;
  }
  return i / j;
}

template<typename T>
Expected<T> div_checked(T i, T j) {
  if (j == T(0)) {
    return Unexpected("Int::div");
  }
  return Expected<T>(div(i, j));
}

template<typename T>
T mod(T i, T j) {
  if (j == T(-1)) {
    // Can cause overflow issues, at least where i is min value.
    return T(0);
  }
  return i % j;
}

template<typename T>
Expected<T> mod_checked(T i, T j) {
  if (j == T(0)) {
    return Unexpected("Int::mod");
  }
  return Expected<T>(mod(i, j));
}

template<typename T>
T mul(T i, T j) {
  return to_signed(to_unsigned(i) * to_unsigned(j));
}

template<typename T>
T sub(T i, T j) {
  return to_signed(to_unsigned(i) - to_unsigned(j));
}

template<typename T>
T neg(T i) {
  return sub(T(0), i);
}

Expected<int32_t> to_int32(int64_t i) {
  if (i < int32_min || i > int32_max) {
    return Unexpected("Int64::toInt32");
  }
  return Expected<int32_t>(int32_t(i));
}

namespace {
  uint64_t char_to_digit(char c) {
    if ('0' <= c && c <= '9') return c - '0';
    if ('a' <= c && c <= 'z') return c - 'a' + 10;
    if ('A' <= c && c <= 'Z') return c - 'A' + 10;
    return uint64_max;
  }
}

Expected<int64_t> to_int64(const char* s, int32_t base) {
    if (!s || base < 2 || base > 36) {
      return Unexpected("String::toInt64 bad base");
    }
    uint64_t ubase = uint64_t(base);
    while (std::isspace(*s)) {
      s += 1;
    }
    // Negative.
    bool is_neg = false;
    if (*s == '-') {
      is_neg = true;
      s += 1;
    }
    // Parse.
    if (!*s) {
      return Unexpected("String::toInt64");
    }
    uint64_t acc = 0;
    uint64_t limit = uint64_half;
    if (!is_neg) {
      limit -= 1;
    }
    for (; *s; ++s) {
        uint64_t d = char_to_digit(*s);
        if (d >= ubase) {
          // TODO Allow only space for trailing.
          break;
        }
        if (acc > (limit - d) / ubase) {
          return Unexpected("String::toInt64 overflow");
        }
        acc = acc * ubase + d;
    }
    int64_t sacc = to_signed(acc);
    return Expected<int64_t>(is_neg ? neg(sacc) : sacc);
}

Expected<int64_t> to_int64(const String& s, int32_t base) {
  return to_int64(s->c_str(), base);
}

Expected<int32_t> to_int32(const String& s, int32_t base) {
  return to_int32(to_int64(s, base).value());
}

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_INT_HPP
