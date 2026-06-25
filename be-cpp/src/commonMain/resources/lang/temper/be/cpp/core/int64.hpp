#pragma once
#include <algorithm>
#include <cstdint>
#include <limits>
#include <string>
#include "temper_bubble.hpp"
#include "base_types.hpp"

namespace temper {
    namespace core {

        namespace Int64 {

            inline int64_t max(int64_t a, int64_t b) {
                return std::max(a, b);
            }

            inline int64_t min(int64_t a, int64_t b) {
                return std::min(a, b);
            }

            inline std::string toString(int64_t i) {
                return std::to_string(i);
            }

            inline std::string toString(int64_t i, int32_t radix) {
                if (radix < 2 || radix > 36) {
                    bubble<std::string>("radix must be between 2 and 36");
                }
                if (radix == 10) {
                    return std::to_string(i);
                }
                std::string result;
                bool is_negative = (i < 0);
                uint64_t n;
                if (is_negative) {
                    // Negate as unsigned to handle INT64_MIN without overflow.
                    n = ~static_cast<uint64_t>(i) + 1ULL;
                } else {
                    n = static_cast<uint64_t>(i);
                }
                if (n == 0) {
                    return "0";
                }
                while (n > 0) {
                    int32_t digit = static_cast<int32_t>(n % static_cast<uint64_t>(radix));
                    char ch;
                    if (digit < 10) {
                        ch = static_cast<char>('0' + digit);
                    } else {
                        ch = static_cast<char>('a' + digit - 10);
                    }
                    result = std::string(1, ch).append(result);
                    n /= static_cast<uint64_t>(radix);
                }
                if (is_negative) {
                    result = std::string("-").append(result);
                }
                return result;
            }

            inline double toFloat64(int64_t i) {
                const int64_t maxSafe = 9007199254740991LL;
                const int64_t minSafe = -9007199254740991LL;
                if (i < minSafe || i > maxSafe) {
                    bubble("Int64 out of safe Float64 range");
                }
                return static_cast<double>(i);
            }

            inline double toFloat64Unsafe(int64_t i) {
                return static_cast<double>(i);
            }

            inline int32_t toInt32(int64_t i) {
                if (i > static_cast<int64_t>(std::numeric_limits<int32_t>::max())
                || i < static_cast<int64_t>(std::numeric_limits<int32_t>::min())) {
                    bubble("Int64 out of Int32 range");
                }
                return static_cast<int32_t>(i);
            }

            inline int32_t toInt32Unsafe(int64_t i) {
                return static_cast<int32_t>(i);
            }

            // See int.hpp for the `_wrap` (general, zero-checked) vs `_safe` (divisor
            // statically non-zero, zero-check elided) distinction. Both keep the
            // `INT64_MIN / -1` guard because that division is UB in C++ regardless.
            inline int64_t div_wrap(int64_t a, int64_t b) {
                if (b == 0) {
                    bubble("division by zero");
                }
                if (a == std::numeric_limits<int64_t>::min() && b == -1) {
                    return std::numeric_limits<int64_t>::min();
                }
                return a / b;
            }

            // Divisor is statically known to be non-zero; the zero check is elided.
            inline int64_t div_safe(int64_t a, int64_t b) {
                if (a == std::numeric_limits<int64_t>::min() && b == -1) {
                    return std::numeric_limits<int64_t>::min();
                }
                return a / b;
            }

            inline int64_t mod_wrap(int64_t a, int64_t b) {
                if (b == 0) {
                    bubble("division by zero");
                }
                if (b == -1 && a == std::numeric_limits<int64_t>::min()) {
                    return 0;
                }
                return a % b;
            }

            // Divisor is statically known to be non-zero; the zero check is elided.
            inline int64_t mod_safe(int64_t a, int64_t b) {
                if (b == -1 && a == std::numeric_limits<int64_t>::min()) {
                    return 0;
                }
                return a % b;
            }

            // Wrapping arithmetic (see Int::add); well-defined without `-fwrapv`.
            inline int64_t add(int64_t a, int64_t b) {
                return static_cast<int64_t>(static_cast<uint64_t>(a) + static_cast<uint64_t>(b));
            }

            inline int64_t sub(int64_t a, int64_t b) {
                return static_cast<int64_t>(static_cast<uint64_t>(a) - static_cast<uint64_t>(b));
            }

            inline int64_t mul(int64_t a, int64_t b) {
                return static_cast<int64_t>(static_cast<uint64_t>(a) * static_cast<uint64_t>(b));
            }

            inline int64_t neg(int64_t a) {
                return static_cast<int64_t>(0ull - static_cast<uint64_t>(a));
            }

            inline int64_t shl(int64_t a, int64_t b) {
                return static_cast<int64_t>(static_cast<uint64_t>(a) << (b & 63));
            }

            inline int64_t shr(int64_t a, int64_t b) {
                return a >> (b & 63);
            }

            inline int64_t ushr(int64_t a, int64_t b) {
                return static_cast<int64_t>(static_cast<uint64_t>(a) >> (b & 63));
            }

        }

    }
}
