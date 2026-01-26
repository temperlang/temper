#ifndef TEMPER_CORE_HPP
#define TEMPER_CORE_HPP

#include <algorithm>
#include <cassert>
#include <iostream>
#include <memory>
#include <sstream>
#include <stdexcept>
#include <stdint.h>
#include <string>
#include <utility>
#include <vector>
#include "expected.hpp"
#include "int.hpp"

namespace temper {
namespace core {

void log(const std::string& message) {
  // Flush on purpose.
  std::cout << message << std::endl;
}

void log(const String& message) {
  log(*message);
}

template<class T>
String to_string(const T& item) {
  std::ostringstream ss;
  ss << item;
  return shared<const std::string>(ss.str());
}

#if __cplusplus >= 201103L

template<typename T, typename... Args>
std::shared_ptr<const std::vector<T>> listify(Args&&... args) {
  return std::make_shared<const std::vector<T>>(
    std::vector<T>{std::forward<Args>(args)...}
  );
}

namespace impl {
  void cat(std::stringstream& dst) {
    (void)dst;
  }

  template<class First, class... Rest>
  void cat(std::stringstream& dst, First& fst, Rest... rest) {
    dst << *fst;
    cat(dst, rest...);
  }
}

template<class... Args>
String cat(Args... args) {
  std::stringstream ss;
  impl::cat(ss, args...);
  return std::make_shared<const std::string>(ss.str());
}

#endif // __cplusplus >= 201103L

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_HPP
