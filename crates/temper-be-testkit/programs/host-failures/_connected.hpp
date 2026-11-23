#pragma once

// A specification: the C++ backend returns failures as
// `temper::core::Expected`, and `NotFound` is the library's own failure
// type, made as that backend will make it.

#include <cstdint>
#include <stdexcept>
#include <string>

namespace host_failures {
namespace _connected {

inline temper::core::Expected<std::int32_t, NotFound> hostLookup(const std::string& key) {
    if (key == "bug") {
        throw std::runtime_error("host bug");
    }
    if (key == "a") {
        return 1;
    }
    return temper::core::unexpected(NotFound(key));
}

} // namespace _connected
} // namespace host_failures
