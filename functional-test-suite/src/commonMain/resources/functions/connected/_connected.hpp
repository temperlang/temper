#pragma once

#include <cstdint>
#include <work/init.hpp>

namespace work {
namespace _connected {

using namespace work;

std::int32_t sum(std::int32_t i, std::int32_t j, std::int32_t bonus);

std::int32_t prod(std::shared_ptr<Hidden> const& hidden, std::int32_t j);

} // namespace _connected
} // namespace work
