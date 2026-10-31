#pragma once

#include <cstdint>
#include <memory>
#include <work/init.hpp>

namespace work {
namespace _connected {

// Temper's connected Counter class, with the members Temper would generate.
struct Counter {
    std::int32_t total_ = 0;

    static std::shared_ptr<Counter> make() {
        return std::make_shared<Counter>();
    }

    void add(std::int32_t i) {
        total_ += i;
    }

    std::int32_t total() {
        return total_;
    }
};

} // namespace _connected
} // namespace work
