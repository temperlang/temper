#pragma once
#define _USE_MATH_DEFINES
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <sstream>
#include <string>
#include "base_types.hpp"
#include "nullable_param.hpp"

namespace temper {
    namespace core {

        namespace Float64 {

            inline double e() { return M_E; }
            inline double pi() { return M_PI; }
            inline double abs(double f) { return std::abs(f); }
            inline double acos(double f) { return std::acos(f); }
            inline double asin(double f) { return std::asin(f); }
            inline double atan(double f) { return std::atan(f); }
            inline double atan2(double a, double b) { return std::atan2(a, b); }
            inline double ceil(double f) { return std::ceil(f); }
            inline double cos(double f) { return std::cos(f); }
            inline double cosh(double f) { return std::cosh(f); }
            inline double exp(double f) { return std::exp(f); }
            inline double expm1(double f) { return std::expm1(f); }
            inline double floor(double f) { return std::floor(f); }
            inline double log(double f) { return std::log(f); }
            inline double log10(double f) { return std::log10(f); }
            inline double log1p(double f) { return std::log1p(f); }
            inline double round(double f) { return std::round(f); }
            inline double sin(double f) { return std::sin(f); }
            inline double sinh(double f) { return std::sinh(f); }
            inline double sqrt(double f) { return std::sqrt(f); }
            inline double tan(double f) { return std::tan(f); }
            inline double tanh(double f) { return std::tanh(f); }

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

            inline bool near(
            double a,
            double b,
            NullableParam<double> relTol_opt = NullableParam<double>(),
            NullableParam<double> absTol_opt = NullableParam<double>()
            ) {
                double relTol = relTol_opt.has_value ? relTol_opt.value : 1e-9;
                double absTol = absTol_opt.has_value ? absTol_opt.value : 0.0;
                double margin = std::max(std::max(std::abs(a), std::abs(b)) * relTol, absTol);
                return std::abs(a - b) <= margin;
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

            inline std::string toString(double f) {
                if (std::isnan(f)) {
                    return "NaN";
                }
                if (std::isinf(f)) {
                    return f > 0 ? "Infinity" : "-Infinity";
                }
                if (f == 0.0 && std::signbit(f)) {
                    return "-0.0";
                }
                for (int prec = 1; prec <= 21; ++prec) {
                    std::ostringstream oss;
                    oss.precision(prec);
                    oss << f;
                    std::string s = oss.str();
                    char* end_ptr;
                    double parsed = std::strtod(s.c_str(), &end_ptr);
                    if (parsed == f && *end_ptr == '\0') {
                        if (s.find('.') == std::string::npos) {
                            size_t epos = s.find('e');
                            if (epos != std::string::npos) {
                                s.insert(epos, ".0");
                            } else {
                                s.append(".0");
                            }
                        }
                        return s;
                    }
                }
                std::ostringstream oss;
                oss.precision(17);
                oss << f;
                std::string s = oss.str();
                if (s.find('.') == std::string::npos) {
                    size_t epos = s.find('e');
                    if (epos != std::string::npos) {
                        s.insert(epos, ".0");
                    } else {
                        s.append(".0");
                    }
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

            inline bool lt(double a, double b) { return cmp(a, b) < 0; }
            inline bool le(double a, double b) { return cmp(a, b) <= 0; }
            inline bool gt(double a, double b) { return cmp(a, b) > 0; }
            inline bool ge(double a, double b) { return cmp(a, b) >= 0; }
            inline bool eq(double a, double b) { return cmp(a, b) == 0; }
            inline bool ne(double a, double b) { return cmp(a, b) != 0; }

            template<class T>
            inline std::string toString(const NullableParam<T>& v) {
                return v.has_value ? toString(v.value) : "null";
            }

        }

    }
}
