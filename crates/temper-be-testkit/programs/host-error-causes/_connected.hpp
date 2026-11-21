#pragma once

#include <cstdint>
#include <stdexcept>
#include <string>

namespace host_error_causes {
namespace _connected {

inline std::int32_t hostFails(std::int32_t n) {
    throw std::runtime_error("host bug " + std::to_string(n));
}

} // namespace _connected
} // namespace host_error_causes
