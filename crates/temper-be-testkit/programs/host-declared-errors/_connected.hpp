#pragma once

// The C++ backend returns errors as `temper::core::Expected`, `NotFound`
// the library's own error struct, a `shared_ptr` made by `make` as any
// class's; a host error is an exception of the host's own.

#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>

namespace host_declared_errors {
namespace _connected {

inline temper::core::Expected<std::int32_t, std::shared_ptr<NotFound>> hostLookup(
    const std::string& key) {
    if (key == "bug") {
        throw std::runtime_error("host bug");
    }
    if (key == "a") {
        return 1;
    }
    return temper::core::unexpected(NotFound::make(key));
}

} // namespace _connected
} // namespace host_declared_errors
