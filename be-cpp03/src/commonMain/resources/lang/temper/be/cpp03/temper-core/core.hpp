#ifndef TEMPER_CORE_HPP
#define TEMPER_CORE_HPP

#include <algorithm>
#include <cassert>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <stdint.h>
#include <string>
#include "expected.hpp"
#include "int.hpp"
#include "shared.hpp"

namespace temper {
namespace core {

void log(const std::string& message) {
  // Flush on purpose.
  std::cout << message << std::endl;
}

void log(const Shared<std::string>& message) {
  log(*message);
}

template<class T>
String toString(const T& item) {
  std::ostringstream ss;
  ss << item;
  return shared<std::string>(ss.str());
}

String cat(String strings[], int32_t n) {
  std::ostringstream ss;
  for (int32_t i = 0; i < n; i += 1) {
    ss << *strings[i];
  }
  return shared<std::string>(ss.str());
}

#if __cplusplus >= 201103L

namespace {
  namespace helper {
    void cat(std::stringstream& dst) {
      (void)dst;
    }

    template<class First, class... Rest>
    void cat(std::stringstream& dst, First& fst, Rest... rest) {
      dst << *fst;
      cat(dst, rest...);
    }
  }
}

template<class... Args>
String cat(Args... args) {
  std::stringstream ss;
  helper::cat(ss, args...);
  return shared<std::string>(ss.str());
}

#else // not __cplusplus >= 201103L

String cat(String s) {
  return s;
}

String cat(String s0, String s1) {
  String strings[] = {s0, s1};
  return cat(strings, sizeof(strings) / sizeof(*strings));
}

String cat(String s0, String s1, String s2) {
  String strings[] = {s0, s1, s2};
  return cat(strings, sizeof(strings) / sizeof(*strings));
}

String cat(String s0, String s1, String s2, String s3) {
  String strings[] = {s0, s1, s2, s3};
  return cat(strings, sizeof(strings) / sizeof(*strings));
}

String cat(String s0, String s1, String s2, String s3, String s4) {
  String strings[] = {s0, s1, s2, s3, s4};
  return cat(strings, sizeof(strings) / sizeof(*strings));
}

String cat(String s0, String s1, String s2, String s3, String s4, String s5) {
  String strings[] = {s0, s1, s2, s3, s4, s5};
  return cat(strings, sizeof(strings) / sizeof(*strings));
}

#endif // __cplusplus >= 201103L else

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_HPP
