#ifndef TEMPER_CORE_SHARED_HPP
#define TEMPER_CORE_SHARED_HPP

#include <memory>
#include <string>

namespace temper {
namespace core {

#if __cplusplus >= 201103L

template<typename T>
using Shared = std::shared_ptr<T>;

template<class T, class... Args>
Shared<T> shared(Args&&... args) {
  return std::make_shared<T>(std::forward<Args>(args)...);
}

// Not an ideal place for this but a convenient one.
using String = std::shared_ptr<const std::string>;

#endif // __cplusplus >= 201103L

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_SHARED_HPP
