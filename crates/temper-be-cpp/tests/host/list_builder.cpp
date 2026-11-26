// The runtime's list builder adding a list to itself, for
// `functional.rs`'s `list_builder_self`. Built with `-D_GLIBCXX_DEBUG`,
// whose checked `std::vector` aborts on an insert from its own range,
// which the standard leaves undefined.
#include "temper-core/list_builder.hpp"
#include <cstdio>

namespace lb = temper::core::ListBuilder;

int main() {
    auto list = lb::make<int32_t>();
    lb::add<int32_t>(list, 1);
    lb::add<int32_t>(list, 2);
    lb::addAll<int32_t>(list, list);
    lb::addAll<int32_t>(list, list, 1);
    lb::splice<int32_t>(list, 1, 2, list);
    for (int32_t x : *list) {
        std::printf("%d ", x);
    }
    std::printf("\n");
    return 0;
}
