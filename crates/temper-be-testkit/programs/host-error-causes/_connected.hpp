#pragma once

#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>

namespace host_error_causes {
namespace _connected {

inline temper::core::Expected<std::int32_t, std::shared_ptr<Oops>> hostFails(
    std::int32_t n) {
    throw std::runtime_error("host bug " + std::to_string(n));
}

} // namespace _connected
} // namespace host_error_causes
