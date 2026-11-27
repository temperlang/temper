#pragma once

#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace host_error_tests {
namespace _connected {

inline temper::core::Expected<std::int32_t, std::shared_ptr<Oops>> hostFails(
    std::int32_t n) {
    switch (n) {
    case 1:
        throw std::runtime_error("host bug " + std::to_string(n));
    case 2:
        return std::vector<std::int32_t>().at(static_cast<std::size_t>(n));
    default:
        throw n;
    }
}

// The test runner's own failure, as a failed assertion throws.
inline void hostFailsTest() {
    throw temper::core::TemperTestFailure("host test failure");
}

} // namespace _connected
} // namespace host_error_tests
