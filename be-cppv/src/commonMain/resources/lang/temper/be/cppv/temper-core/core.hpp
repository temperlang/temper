#ifndef TEMPER_CORE_HPP
#define TEMPER_CORE_HPP

#include <algorithm>
#include <cassert>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <stdint.h>
#include <string>
#include <utility>
#include <vector>
#include "expected.hpp"
#include "int.hpp"
#include "shared.hpp"

namespace temper {
namespace core {

template<typename T, typename... Args>
std::shared_ptr<const std::vector<T>> listify(Args&&... args) {
  return std::make_shared<const std::vector<T>>(
    std::vector<T>{std::forward<Args>(args)...}
  );
}

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
  return shared<std::string>(ss.str());
}

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_HPP
