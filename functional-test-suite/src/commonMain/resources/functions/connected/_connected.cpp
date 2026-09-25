#include "_connected.hpp"
#include "support.hpp"

namespace work {
namespace _connected {

std::int32_t sum(std::int32_t i, std::int32_t j, std::int32_t bonus) {
    return work::sumOf3(i, j, bonus);
}

std::int32_t prod(std::shared_ptr<Hidden> const& hidden, std::int32_t j) {
    Support support;
    return support.prod(hidden->get_i(), j);
}

int32_t length(temper::core::NullableParam<std::string> s) {
    return s.has_value ? s.value.size() : -1;
}

} // namespace _connected
} // namespace work
