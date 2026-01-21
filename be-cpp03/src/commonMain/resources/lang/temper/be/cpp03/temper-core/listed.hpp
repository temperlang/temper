#ifndef TEMPER_CORE_LISTED_HPP
#define TEMPER_CORE_LISTED_HPP

#include <vector>
#if __cplusplus >= 201103L
# include <utility>
#endif
#include "shared.hpp"

namespace temper {
namespace core {

#if __cplusplus >= 201103L

// --- Modern C++11+ Implementation ---
// Uses raw vector for direct access speed, then moves into Shared.
template<typename T>
class Listify {
  std::vector<T> items;

public:
  explicit Listify(size_t capacity) {
    items.reserve(capacity);
  }

  Listify& add(const T& item) {
    items.push_back(item);
    return *this;
  }

  Listify& add(T&& item) {
    items.push_back(std::move(item));
    return *this;
  }

  Shared<const std::vector<T>> to_list() {
    return shared<const std::vector<T>>(std::move(items));
  }
};

#else

// --- Legacy C++03 Implementation ---
// Uses Shared full-time to avoid a deep copy of the vector during build().
template<typename T>
class Listify {
  Shared<std::vector<T> > items;

public:
  explicit Listify(size_t capacity): items(shared<std::vector<T> >()) {
    items->reserve(capacity);
  }

  Listify& add(const T& item) {
    items->push_back(item);
    return *this;
  }

  Shared<const std::vector<T> > to_list() {
    Shared<const std::vector<T> > result = items;
    items.reset();
    return result;
  }
};

#endif

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_LISTED_HPP
