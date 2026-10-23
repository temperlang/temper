#pragma once

#include <cstdint>
#include <work/init.hpp>

namespace work {
namespace _connected {

using namespace work;

std::int32_t sum(std::int32_t i, std::int32_t j, std::int32_t bonus);

std::int32_t prod(std::shared_ptr<Hidden> const& hidden, std::int32_t j);

int32_t length(temper::core::NullableParam<std::string> s);

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
