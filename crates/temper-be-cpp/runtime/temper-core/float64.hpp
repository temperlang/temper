#pragma once
#define _USE_MATH_DEFINES
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <limits>
#include <string>
#include "base_types.hpp"
#include "nullable_param.hpp"

namespace temper {
    namespace core {

        namespace Float64 {

            // Literals, not M_E and M_PI: MSVC defines those only when
            // _USE_MATH_DEFINES comes before the first <cmath> or
            // <math.h>, which a header included earlier may already have
            // brought in.
            inline double e() {
                return 2.718281828459045;
            }
            inline double pi() {
                return 3.141592653589793;
            }
            inline double abs(double f) {
                return std::abs(f);
            }
            inline double acos(double f) {
                return std::acos(f);
            }
            inline double asin(double f) {
                return std::asin(f);
            }
            inline double atan(double f) {
                return std::atan(f);
            }
            inline double atan2(double a, double b) {
                return std::atan2(a, b);
            }
            inline double ceil(double f) {
                return std::ceil(f);
            }
            inline double cos(double f) {
                return std::cos(f);
            }
            inline double cosh(double f) {
                return std::cosh(f);
            }
            inline double exp(double f) {
                return std::exp(f);
            }
            inline double expm1(double f) {
                return std::expm1(f);
            }
            inline double floor(double f) {
                return std::floor(f);
            }
            inline double log(double f) {
                return std::log(f);
            }
            inline double log10(double f) {
                return std::log10(f);
            }
            inline double log1p(double f) {
                return std::log1p(f);
            }
            inline double round(double f) {
                return std::round(f);
            }
            inline double sin(double f) {
                return std::sin(f);
            }
            inline double sinh(double f) {
                return std::sinh(f);
            }
            inline double sqrt(double f) {
                return std::sqrt(f);
            }
            inline double tan(double f) {
                return std::tan(f);
            }
            inline double tanh(double f) {
                return std::tanh(f);
            }

            inline double max(double a, double b) {
                if (std::isnan(a) || std::isnan(b)) {
                    return std::numeric_limits<double>::quiet_NaN();
                }
                return std::max(a, b);
            }

            inline double min(double a, double b) {
                if (std::isnan(a) || std::isnan(b)) {
                    return std::numeric_limits<double>::quiet_NaN();
                }
                return std::min(a, b);
            }

            inline double sign(double f) {
                if (f > 0) {
                    return 1.0;
                }
                if (f < 0) {
                    return -1.0;
                }
                return std::copysign(0.0, f);
            }

            inline double pow(double base, double exponent) {
                return std::pow(base, exponent);
            }

            inline int32_t toInt32(double f) {
                if (std::isnan(f)
                || std::isinf(f)
                || f > static_cast<double>(std::numeric_limits<int32_t>::max())
                || f < static_cast<double>(std::numeric_limits<int32_t>::min())) {
                    bubble("Float64 out of Int32 range");
                }
                return static_cast<int32_t>(f);
            }

            inline int32_t toInt32Unsafe(double f) {
                return static_cast<int32_t>(f);
            }

            inline int64_t toInt64(double f) {
                const double maxSafe = 9007199254740991.0;
                const double minSafe = -9007199254740991.0;
                if (!(f >= minSafe && f <= maxSafe)) {
                    bubble("Float64 out of safe Int64 range");
                }
                return static_cast<int64_t>(f);
            }

            inline int64_t toInt64Unsafe(double f) {
                return static_cast<int64_t>(f);
            }

            // Java's `Double.toString`, as Kotlin's interpreter and the
            // Java and Rust runtimes give it, with `e+N` exponents: plain
            // for 1e-3 <= |f| < 1e7, else one digit before the point; the
            // shortest digits that read back as `f`, but at least two, the
            // closest such (`4.9e-324`); and a digit after the point
            // always (`1500.0`, `1.0e+7`). `%.*e` rounds correctly (glibc,
            // and MSVC's UCRT), so the first precision that reads back has
            // the closest digits of that length; `std::to_chars` would do
            // it all, but is C++17 and missing for doubles in older
            // standard libraries.
            inline Str toString(double f) {
                if (std::isnan(f)) {
                    return "NaN";
                }
                if (std::isinf(f)) {
                    return f > 0 ? "Infinity" : "-Infinity";
                }
                if (f == 0.0) {
                    return std::signbit(f) ? "-0.0" : "0.0";
                }
                // Shortest digits, without sign or point, and the exponent.
                std::string digits;
                int exponent = 0;
                // At a power of two, the gap below is half that above, so
                // the closest digits of a length may not read back where
                // the next ones up do (`7.120236347223045e-307`, 2^-1017).
                double magnitude = std::fabs(f);
                int binary_exponent;
                bool power_of_two = std::frexp(magnitude, &binary_exponent) == 0.5;
                for (int prec = 1; prec <= 16; ++prec) {
                    // "-d.ddddddddddddddddde-308" and a NUL fit.
                    char buf[32];
                    std::snprintf(buf, sizeof buf, "%.*e", prec, magnitude);
                    // Whatever the C locale's point is.
                    digits.clear();
                    const char* p = buf;
                    for (; *p != '\0' && *p != 'e'; ++p) {
                        if (*p >= '0' && *p <= '9') {
                            digits.push_back(*p);
                        }
                    }
                    exponent = std::atoi(p + 1);
                    if (std::strtod(buf, nullptr) == magnitude || prec == 16) {
                        break;
                    }
                    if (power_of_two) {
                        std::string up = digits;
                        size_t i = up.size();
                        while (i > 0 && up[i - 1] == '9') {
                            up[--i] = '0';
                        }
                        int up_exponent = exponent;
                        if (i == 0) {
                            up.insert(0, 1, '1');
                            up.pop_back();
                            ++up_exponent;
                        } else {
                            ++up[i - 1];
                        }
                        // Whole digits, which read the same in every locale.
                        std::string text = up + "e" + std::to_string(up_exponent - prec);
                        if (std::strtod(text.c_str(), nullptr) == magnitude) {
                            digits = up;
                            exponent = up_exponent;
                            break;
                        }
                    }
                }
                bool negative = f < 0;
                size_t last = digits.find_last_not_of('0');
                digits.erase(last == std::string::npos ? 1 : last + 1);
                std::string s;
                if (negative) {
                    s.push_back('-');
                }
                if (exponent >= -3 && exponent < 7) {
                    if (exponent < 0) {
                        s.append("0.");
                        s.append(static_cast<size_t>(-exponent - 1), '0');
                        s.append(digits);
                    } else {
                        size_t whole = static_cast<size_t>(exponent) + 1;
                        if (digits.size() > whole) {
                            s.append(digits, 0, whole);
                            s.push_back('.');
                            s.append(digits, whole, std::string::npos);
                        } else {
                            s.append(digits);
                            s.append(whole - digits.size(), '0');
                            s.append(".0");
                        }
                    }
                } else {
                    s.push_back(digits[0]);
                    s.push_back('.');
                    if (digits.size() > 1) {
                        s.append(digits, 1, std::string::npos);
                    } else {
                        s.push_back('0');
                    }
                    s.append(exponent < 0 ? "e-" : "e+");
                    s.append(std::to_string(exponent < 0 ? -exponent : exponent));
                }
                return s;
            }

            inline int32_t cmp(double a, double b) {
                bool a_nan = std::isnan(a);
                bool b_nan = std::isnan(b);
                if (a_nan && b_nan) {
                    return 0;
                }
                if (a_nan) {
                    return 1;
                }
                if (b_nan) {
                    return -1;
                }
                if (a == 0.0 && b == 0.0) {
                    bool a_neg = std::signbit(a);
                    bool b_neg = std::signbit(b);
                    if (a_neg && !b_neg) {
                        return -1;
                    }
                    if (!a_neg && b_neg) {
                        return 1;
                    }
                    return 0;
                }
                if (a < b) {
                    return -1;
                }
                if (a > b) {
                    return 1;
                }
                return 0;
            }

            inline bool lt(double a, double b) {
                return cmp(a, b) < 0;
            }
            inline bool le(double a, double b) {
                return cmp(a, b) <= 0;
            }
            inline bool gt(double a, double b) {
                return cmp(a, b) > 0;
            }
            inline bool ge(double a, double b) {
                return cmp(a, b) >= 0;
            }
            inline bool eq(double a, double b) {
                return cmp(a, b) == 0;
            }
            inline bool ne(double a, double b) {
                return cmp(a, b) != 0;
            }

            // Equal by Temper's `==`, so NaN is near NaN, or finite and
            // within the margin, which a NaN margin never is: `max` keeps
            // NaN.
            inline bool near(
            double a,
            double b,
            NullableParam<double> relTol_opt = NullableParam<double>(),
            NullableParam<double> absTol_opt = NullableParam<double>()
            ) {
                double relTol = relTol_opt.has_value ? relTol_opt.value : 1e-9;
                double absTol = absTol_opt.has_value ? absTol_opt.value : 0.0;
                double margin = max(max(std::abs(a), std::abs(b)) * relTol, absTol);
                return eq(a, b)
                    || (!std::isinf(a) && !std::isinf(b) && std::abs(a - b) <= margin);
            }

            template<class T>
            inline Str toString(const NullableParam<T>& v) {
                return v.has_value ? toString(v.value) : TEMPER_STR("null");
            }

        }

    }
}
