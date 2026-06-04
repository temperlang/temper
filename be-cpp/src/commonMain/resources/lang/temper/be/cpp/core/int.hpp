#pragma once
#include <algorithm>
#include <cstdint>
#include <limits>
#include <string>
#include "temper_bubble.hpp"
#include "base_types.hpp"

namespace temper {
    namespace core {

        namespace Int {

            inline int32_t max(int32_t a, int32_t b) {
                return std::max(a, b);
            }

            inline int32_t min(int32_t a, int32_t b) {
                return std::min(a, b);
            }

            inline std::string toString(int32_t i) {
                return std::to_string(i);
            }

            inline std::string toString(int32_t i, int32_t radix) {
                if (radix < 2 || radix > 36) {
                    bubble<std::string>("radix must be between 2 and 36");
                }
                if (radix == 10) {
                    return std::to_string(i);
                }
                std::string result;
                bool is_negative = (i < 0);
                uint32_t n;
                if (is_negative) {
                    n = static_cast<uint32_t>(-static_cast<int64_t>(i));
                } else {
                    n = static_cast<uint32_t>(i);
                }
                if (n == 0) {
                    return "0";
                }
                while (n > 0) {
                    int32_t digit = n % radix;
                    char ch;
                    if (digit < 10) {
                        ch = static_cast<char>('0' + digit);
                    } else {
                        ch = static_cast<char>('a' + digit - 10);
                    }
                    result = std::string(1, ch).append(result);
                    n /= radix;
                }
                if (is_negative) {
                    result = std::string("-").append(result);
                }
                return result;
            }

            inline double toFloat64(int32_t i) {
                return static_cast<double>(i);
            }

            inline double toFloat64Unsafe(int32_t i) {
                return static_cast<double>(i);
            }

            inline int64_t toInt64(int32_t i) {
                return static_cast<int64_t>(i);
            }

            // General integer division (`DivIntInt`): the divisor may be zero, so we
            // bubble on zero. The `_safe` variant is emitted by the frontend only when
            // the divisor is statically known to be non-zero (`DivIntIntSafe`) and
            // elides that check. This mirrors Rust's `int_div` (checked) vs
            // `wrapping_div` (unchecked). Both variants must keep the `INT_MIN / -1`
            // guard: that division is undefined behavior in C++ (unlike Rust's
            // `wrapping_div`), independent of whether the divisor is zero.
            inline int32_t div_wrap(int32_t a, int32_t b) {
                if (b == 0) {
                    bubble("division by zero");
                }
                if (a == std::numeric_limits<int32_t>::min() && b == -1) {
                    return std::numeric_limits<int32_t>::min();
                }
                return a / b;
            }

            // Divisor is statically known to be non-zero; the zero check is elided. The
            // `INT_MIN / -1` guard remains because that case is UB regardless.
            inline int32_t div_safe(int32_t a, int32_t b) {
                if (a == std::numeric_limits<int32_t>::min() && b == -1) {
                    return std::numeric_limits<int32_t>::min();
                }
                return a / b;
            }

            inline int32_t mod_wrap(int32_t a, int32_t b) {
                if (b == 0) {
                    bubble("division by zero");
                }
                if (b == -1 && a == std::numeric_limits<int32_t>::min()) {
                    return 0;
                }
                return a % b;
            }

            // Divisor is statically known to be non-zero; the zero check is elided. The
            // `INT_MIN % -1` guard remains because that case is UB regardless.
            inline int32_t mod_safe(int32_t a, int32_t b) {
                if (b == -1 && a == std::numeric_limits<int32_t>::min()) {
                    return 0;
                }
                return a % b;
            }

            // Temper Int arithmetic wraps on overflow (two's complement). Doing the math
            // in unsigned makes the result well-defined without relying on the `-fwrapv`
            // compiler flag, so the emitted code is self-contained for any consumer.
            inline int32_t add(int32_t a, int32_t b) {
                return static_cast<int32_t>(static_cast<uint32_t>(a) + static_cast<uint32_t>(b));
            }

            inline int32_t sub(int32_t a, int32_t b) {
                return static_cast<int32_t>(static_cast<uint32_t>(a) - static_cast<uint32_t>(b));
            }

            inline int32_t mul(int32_t a, int32_t b) {
                return static_cast<int32_t>(static_cast<uint32_t>(a) * static_cast<uint32_t>(b));
            }

            inline int32_t neg(int32_t a) {
                return static_cast<int32_t>(0u - static_cast<uint32_t>(a));
            }

            inline int32_t shl(int32_t a, int32_t b) {
                return static_cast<int32_t>(static_cast<uint32_t>(a) << (b & 31));
            }

            inline int32_t shr(int32_t a, int32_t b) {
                return a >> (b & 31);
            }

            inline int32_t ushr(int32_t a, int32_t b) {
                return static_cast<int32_t>(static_cast<uint32_t>(a) >> (b & 31));
            }

        }

    }
}
