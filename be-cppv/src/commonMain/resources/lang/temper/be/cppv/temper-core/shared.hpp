#ifndef TEMPER_CORE_SHARED_HPP
#define TEMPER_CORE_SHARED_HPP

#include <cassert>
#include <memory>
#include <string>

namespace temper {
namespace core {

template<typename T>
using Shared = std::shared_ptr<T>;

template<class T, class... Args>
Shared<T> shared(Args&&... args) {
  return std::make_shared<T>(std::forward<Args>(args)...);
}

typedef Shared<std::string> String;

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_SHARED_HPP
