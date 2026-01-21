#ifndef TEMPER_CORE_LISTED_HPP
#define TEMPER_CORE_LISTED_HPP

#include <vector>
#include "shared.hpp"

namespace temper {
namespace core {

template<typename T>
class Listify {
  Shared<std::vector<T> > items;

public:
  Listify(size_t capacity): items(shared<std::vector<T> >()) {
    items->reserve(capacity);
  }

  Listify& add(const T& item) {
    items->push_back(item);
    return *this;
  }

  Shared<const std::vector<T> > build() {
    Shared<const std::vector<T> > result = items;
    items.reset();
    return result;
  }
};

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_LISTED_HPP
