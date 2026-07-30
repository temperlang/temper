#include "_connected.hpp"
#include "support.hpp"

namespace work {
namespace _connected {

std::int32_t sum(std::int32_t i, std::int32_t j, std::int32_t bonus) {
    return i + j + bonus;
}

std::int32_t prod(std::shared_ptr<Hidden> const& hidden, std::int32_t j) {
    Support support;
    return support.prod(hidden->get_i(), j);
}

} // namespace _connected
} // namespace work
