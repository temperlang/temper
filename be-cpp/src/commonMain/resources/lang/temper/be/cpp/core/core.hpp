#ifndef TEMPER_CORE_HPP
#define TEMPER_CORE_HPP

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <deque>
#include <functional>
#include <iostream>
#include <limits>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

namespace temper {
    namespace core {
        namespace helpers {
            namespace {
                void cat(std::stringstream &dst) {
                    (void) dst;
                }

                template<class First, class... Rest>
                void cat(std::stringstream &dst, First fst, Rest... rest) {
                    dst << fst;
                    cat(dst, rest...);
                }

                template<class Base, class Enable = void>
                struct ObjectHelper {
                    using Type = std::shared_ptr<Base>;
                };

                template<>
                struct ObjectHelper<void> {
                    using Type = void;
                };

                // Specialize for value types that don't need heap allocation.
                template<>
                struct ObjectHelper<std::string> {
                    using Type = std::string;
                };

                template<>
                struct ObjectHelper<bool> {
                    using Type = bool;
                };

                template<>
                struct ObjectHelper<int32_t> {
                    using Type = int32_t;
                };

                template<>
                struct ObjectHelper<int64_t> {
                    using Type = int64_t;
                };

                template<>
                struct ObjectHelper<double> {
                    using Type = double;
                };

                // shared_ptr types should not be double-wrapped
                template<class T>
                struct ObjectHelper<std::shared_ptr<T>> {
                    using Type = std::shared_ptr<T>;
                };

            }
        }

        // AnyValue: type-erased value holder using polymorphic base
        // Defined outside anonymous namespace to avoid linkage warnings
        struct AnyValueBase {
            virtual ~AnyValueBase() = default;
        };

        // Box for value types stored as AnyValue
        template<class T>
        struct AnyValueBox : AnyValueBase {
            T value;
            AnyValueBox(T v) : value(std::move(v)) {}
        };

        namespace {
            using Void = void;
            using Boolean = bool;
            using Int = int32_t;
            using Int64 = int64_t;
            using Float64 = double;
            using String = std::string;
            using StringIndex = int32_t;
            using StringIndexOption = int32_t;
            using NoStringIndex = int32_t;
            using Type = void*;

            using AnyValue = std::shared_ptr<AnyValueBase>;

            // Box a value into AnyValue
            template<class T>
            AnyValue any_box(T value) {
                return std::make_shared<AnyValueBox<T>>(std::move(value));
            }

            // Never type — implicitly converts to any type (used with [[noreturn]] functions)
            struct Never {
                template<class T> operator T() const { std::terminate(); }
            };

            // Empty type — Temper's unit type
            struct Empty {};
            inline std::shared_ptr<Empty> empty() {
                return std::make_shared<Empty>();
            }

            template<class Ret, class... Params>
            using Function = std::function<Ret(Params...)>;

            // lists
            template<class Elem>
            using Listed = std::shared_ptr<std::vector<Elem>>;

            template<class Elem>
            using List = std::shared_ptr<std::vector<Elem>>;

            template<class Elem>
            using ListBuilder = std::shared_ptr<std::vector<Elem>>;

            // maps
            // Insertion-ordered map: preserves insertion order for iteration
            template<class Key, class Value>
            struct OrderedMap {
                std::vector<Key> order;
                std::map<Key, Value> data;
            };

            template<class Key, class Value>
            using Mapped = std::shared_ptr<OrderedMap<Key, Value>>;

            template<class Key, class Value>
            using Map = std::shared_ptr<OrderedMap<Key, Value>>;

            template<class Key, class Value>
            using MapBuilder = std::shared_ptr<OrderedMap<Key, Value>>;

            // wrapper
            template<class Base>
            using Object = typename helpers::ObjectHelper<Base>::Type;

            // Create a non-owning shared_ptr from a raw this pointer,
            // for passing `this` to functions expecting Object<T>.
            template<class T>
            Object<T> borrow_this(T* ptr) {
                return std::shared_ptr<T>(std::shared_ptr<T>{}, ptr);
            }

            template<class Base>
            using Nullable = Base;

            // NullableParam: used for optional function parameters
            // and nullable value types to distinguish null from default
            template<class T>
            struct NullableParam {
                T value;
                bool has_value;
                NullableParam() : value(), has_value(false) {}
                NullableParam(T v) : value(std::move(v)), has_value(true) {}
                NullableParam(std::nullptr_t) : value(), has_value(false) {}
                // Allow construction from const char* for NullableParam<string>
                template<class U, typename std::enable_if<
                    std::is_convertible<U, T>::value && !std::is_same<U, T>::value, int>::type = 0>
                NullableParam(U&& v) : value(std::forward<U>(v)), has_value(true) {}
                NullableParam& operator=(T v) { value = std::move(v); has_value = true; return *this; }
                NullableParam& operator=(std::nullptr_t) { value = T(); has_value = false; return *this; }
                operator T() const { return value; }
            };

            template<class Base>
            using Bubble = Base;

            // Forward declaration for bubble (error handling)
            // Template so bubble() can appear in expression context (e.g. assignment)
            template<class T = void>
            [[noreturn]] T bubble(String message);
            template<class T = void>
            [[noreturn]] T bubble();

            // object construction for shared_ptr types
            template<class Base, class... Fields>
            typename std::enable_if<!std::is_same<Object<Base>, Base>::value, Object<Base>>::type
            object(Fields... fields) {
                return std::make_shared<Base>(fields...);
            }

            // For value types, just construct directly.
            template<class Base, class... Fields>
            typename std::enable_if<std::is_same<Object<Base>, Base>::value, Object<Base>>::type
            object(Fields... fields) {
                return Base(fields...);
            }

            // String concatenation
            template<class... Args>
            String cat(Args... args) {
                std::stringstream ss;
                helpers::cat(ss, args...);
                return ss.str();
            }

            // ==================== Console ====================
            struct Console {
                std::function<Void(String)> logger;
                Console() = default;
                explicit Console(std::function<Void(String)> logger) : logger(std::move(logger)) {}
            };

            void log(Object<Console> console, String msg) {
                console->logger(msg + "\n");
            }

            Object<Console> get_console() {
                return object<Console>(std::function<Void(String)>([](String s) {
                    std::cout << s;
                }));
            }

            // ==================== Printing ====================
            String toString(bool b) {
                return b ? "true" : "false";
            }

            String toString(Int i) {
                return std::to_string(i);
            }

            String toString(Int i, Int radix) {
                if (radix == 10) return std::to_string(i);
                std::string result;
                unsigned int n = static_cast<unsigned int>(i);
                if (n == 0) return "0";
                while (n > 0) {
                    int digit = n % radix;
                    result = char(digit < 10 ? '0' + digit : 'a' + digit - 10) + result;
                    n /= radix;
                }
                return result;
            }

            String toString(Int64 i) {
                return std::to_string(i);
            }

            String toString(Float64 f) {
                if (std::isnan(f)) return "NaN";
                if (std::isinf(f)) return f > 0 ? "Infinity" : "-Infinity";
                if (f == 0.0 && std::signbit(f)) return "-0.0";
                // Try shortest representation that round-trips
                for (int prec = 1; prec <= 21; ++prec) {
                    std::ostringstream oss;
                    oss.precision(prec);
                    oss << f;
                    std::string s = oss.str();
                    // Check if this representation round-trips
                    char *end;
                    double parsed = std::strtod(s.c_str(), &end);
                    if (parsed == f && *end == '\0') {
                        // Ensure decimal point is present for float formatting
                        if (s.find('.') == std::string::npos) {
                            size_t epos = s.find('e');
                            if (epos != std::string::npos) {
                                s.insert(epos, ".0");
                            } else {
                                s += ".0";
                            }
                        }
                        return s;
                    }
                }
                // Fallback: maximum precision
                std::ostringstream oss;
                oss.precision(17);
                oss << f;
                std::string s = oss.str();
                if (s.find('.') == std::string::npos) {
                    size_t epos = s.find('e');
                    if (epos != std::string::npos) {
                        s.insert(epos, ".0");
                    } else {
                        s += ".0";
                    }
                }
                return s;
            }

            String toString(String s) {
                return s;
            }

            // ==================== Int operations ====================
            Int max(Int a, Int b) { return std::max(a, b); }
            Int min(Int a, Int b) { return std::min(a, b); }
            Float64 toFloat64(Int i) { return static_cast<Float64>(i); }
            Float64 toFloat64Unsafe(Int i) { return static_cast<Float64>(i); }

            // ==================== Int64 operations ====================
            Int64 toInt64(Int i) { return static_cast<Int64>(i); }
            Int64 max64(Int64 a, Int64 b) { return std::max(a, b); }
            Int64 min64(Int64 a, Int64 b) { return std::min(a, b); }
            Float64 toFloat64_64(Int64 i) {
                const Int64 maxSafe = 9007199254740991LL;  // 2^53 - 1
                const Int64 minSafe = -9007199254740991LL;
                if (i < minSafe || i > maxSafe) {
                    bubble("Int64 out of safe Float64 range");
                }
                return static_cast<Float64>(i);
            }
            Float64 toFloat64Unsafe_64(Int64 i) { return static_cast<Float64>(i); }
            Int toInt32(Int64 i) {
                if (i > static_cast<Int64>(std::numeric_limits<Int>::max()) ||
                    i < static_cast<Int64>(std::numeric_limits<Int>::min())) {
                    bubble("Int64 out of Int32 range");
                }
                return static_cast<Int>(i);
            }
            Int toInt32Unsafe(Int64 i) { return static_cast<Int>(i); }
            String toString64(Int64 i) { return std::to_string(i); }
            Int64 toInt64f(Float64 f) {
                // Safe range: MIN_SAFE_INTEGER to MAX_SAFE_INTEGER (-(2^53-1) to 2^53-1)
                const Float64 maxSafe = 9007199254740991.0;  // 2^53 - 1
                const Float64 minSafe = -9007199254740991.0;
                if (!(f >= minSafe && f <= maxSafe)) {
                    bubble("Float64 out of safe Int64 range");
                }
                return static_cast<Int64>(f);
            }
            Int64 toInt64Unsafef(Float64 f) { return static_cast<Int64>(f); }

            // ==================== Float64 operations ====================
            namespace Float64ns {
                Float64 e() { return M_E; }
                Float64 pi() { return M_PI; }
            }

            Float64 abs(Float64 f) { return std::abs(f); }
            Float64 acos(Float64 f) { return std::acos(f); }
            Float64 asin(Float64 f) { return std::asin(f); }
            Float64 atan(Float64 f) { return std::atan(f); }
            Float64 atan2(Float64 a, Float64 b) { return std::atan2(a, b); }
            Float64 ceil(Float64 f) { return std::ceil(f); }
            Float64 cos(Float64 f) { return std::cos(f); }
            Float64 cosh(Float64 f) { return std::cosh(f); }
            Float64 exp(Float64 f) { return std::exp(f); }
            Float64 expm1(Float64 f) { return std::expm1(f); }
            Float64 floor(Float64 f) { return std::floor(f); }
            Float64 log(Float64 f) { return std::log(f); }
            Float64 log10(Float64 f) { return std::log10(f); }
            Float64 log1p(Float64 f) { return std::log1p(f); }
            Float64 max(Float64 a, Float64 b) {
                if (std::isnan(a) || std::isnan(b)) return std::numeric_limits<double>::quiet_NaN();
                return std::max(a, b);
            }
            Float64 min(Float64 a, Float64 b) {
                if (std::isnan(a) || std::isnan(b)) return std::numeric_limits<double>::quiet_NaN();
                return std::min(a, b);
            }
            Boolean near(Float64 a, Float64 b, Float64 relTol, Float64 absTol) {
                Float64 margin = std::max(std::max(std::abs(a), std::abs(b)) * relTol, absTol);
                return std::abs(a - b) < margin;
            }
            Boolean near(Float64 a, Float64 b, Float64 relTol) {
                return near(a, b, relTol, 0.0);
            }
            Boolean near(Float64 a, Float64 b) {
                return near(a, b, 1e-9, 0.0);
            }
            Float64 round(Float64 f) { return std::round(f); }
            Float64 sign(Float64 f) {
                if (f > 0) return 1.0;
                if (f < 0) return -1.0;
                return std::copysign(0.0, f);
            }
            Float64 sin(Float64 f) { return std::sin(f); }
            Float64 sinh(Float64 f) { return std::sinh(f); }
            Float64 sqrt(Float64 f) { return std::sqrt(f); }
            Float64 tan(Float64 f) { return std::tan(f); }
            Float64 tanh(Float64 f) { return std::tanh(f); }
            Int toInt(Float64 f) {
                if (std::isnan(f) || std::isinf(f) ||
                    f > static_cast<Float64>(std::numeric_limits<Int>::max()) ||
                    f < static_cast<Float64>(std::numeric_limits<Int>::min())) {
                    bubble("Float64 out of Int32 range");
                }
                return static_cast<Int>(f);
            }
            Int toIntUnsafe(Float64 f) { return static_cast<Int>(f); }
            Float64 pow(Float64 base, Float64 exponent) { return std::pow(base, exponent); }

            // ==================== Comparison helpers ====================
            // Temper total ordering for Float64:
            // NaN is greater than everything (including Infinity)
            // -0.0 is less than 0.0
            Int cmp(Float64 a, Float64 b) {
                bool aNaN = std::isnan(a), bNaN = std::isnan(b);
                if (aNaN && bNaN) return 0;
                if (aNaN) return 1;
                if (bNaN) return -1;
                if (a == 0.0 && b == 0.0) {
                    bool aNeg = std::signbit(a), bNeg = std::signbit(b);
                    if (aNeg && !bNeg) return -1;
                    if (!aNeg && bNeg) return 1;
                    return 0;
                }
                if (a < b) return -1;
                if (a > b) return 1;
                return 0;
            }
            Boolean lt(Float64 a, Float64 b) { return cmp(a, b) < 0; }
            Boolean le(Float64 a, Float64 b) { return cmp(a, b) <= 0; }
            Boolean gt(Float64 a, Float64 b) { return cmp(a, b) > 0; }
            Boolean ge(Float64 a, Float64 b) { return cmp(a, b) >= 0; }
            Boolean eq(Float64 a, Float64 b) { return cmp(a, b) == 0; }
            Boolean ne(Float64 a, Float64 b) { return cmp(a, b) != 0; }

            template<class T>
            Boolean lt(T a, T b) { return a < b; }
            template<class T>
            Boolean le(T a, T b) { return a <= b; }
            template<class T>
            Boolean gt(T a, T b) { return a > b; }
            template<class T>
            Boolean ge(T a, T b) { return a >= b; }
            template<class T>
            Boolean eq(T a, T b) { return a == b; }
            // Overload for mixed string/const char* comparisons
            Boolean eq(String a, const char* b) { return a == b; }
            Boolean eq(const char* a, String b) { return a == b; }
            template<class T>
            Boolean ne(T a, T b) { return a != b; }
            Boolean ne(String a, const char* b) { return a != b; }
            Boolean ne(const char* a, String b) { return a != b; }
            template<class T>
            Int cmp(T a, T b) {
                if (a < b) return -1;
                if (a > b) return 1;
                return 0;
            }
            Int cop(Int a, Int b) { return cmp(a, b); }

            // ==================== String construction ====================
            String string_from_codepoint(Int codePoint) {
                // Validate: reject surrogates (0xD800-0xDFFF) and out-of-range (>= 0x110000)
                if (codePoint < 0 || codePoint >= 0x110000 ||
                    (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
                    bubble<String>("invalid code point");
                }
                // UTF-8 encoding
                std::string result;
                if (codePoint < 0x80) {
                    result += static_cast<char>(codePoint);
                } else if (codePoint < 0x800) {
                    result += static_cast<char>(0xC0 | (codePoint >> 6));
                    result += static_cast<char>(0x80 | (codePoint & 0x3F));
                } else if (codePoint < 0x10000) {
                    result += static_cast<char>(0xE0 | (codePoint >> 12));
                    result += static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F));
                    result += static_cast<char>(0x80 | (codePoint & 0x3F));
                } else {
                    result += static_cast<char>(0xF0 | (codePoint >> 18));
                    result += static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F));
                    result += static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F));
                    result += static_cast<char>(0x80 | (codePoint & 0x3F));
                }
                return result;
            }

            String string_from_codepoints(List<Int> codePoints) {
                std::string result;
                for (const auto& cp : *codePoints) {
                    result += string_from_codepoint(cp);
                }
                return result;
            }

            // ==================== String operations (UTF-8 aware) ====================
            Boolean isempty(String s) { return s.empty(); }
            Int begin() { return 0; }
            Int end(String s) { return static_cast<Int>(s.size()); }

            // Helper: number of bytes in the UTF-8 sequence starting at byte b
            inline Int utf8_seq_len(unsigned char b) {
                if (b < 0x80) return 1;
                if ((b & 0xE0) == 0xC0) return 2;
                if ((b & 0xF0) == 0xE0) return 3;
                if ((b & 0xF8) == 0xF0) return 4;
                return 1; // invalid, treat as single byte
            }

            // Read the code point at byte index
            Int get(String s, Int index) {
                unsigned char b0 = static_cast<unsigned char>(s[index]);
                if (b0 < 0x80) return b0;
                if ((b0 & 0xE0) == 0xC0) {
                    return ((b0 & 0x1F) << 6) |
                           (static_cast<unsigned char>(s[index+1]) & 0x3F);
                }
                if ((b0 & 0xF0) == 0xE0) {
                    return ((b0 & 0x0F) << 12) |
                           ((static_cast<unsigned char>(s[index+1]) & 0x3F) << 6) |
                           (static_cast<unsigned char>(s[index+2]) & 0x3F);
                }
                if ((b0 & 0xF8) == 0xF0) {
                    return ((b0 & 0x07) << 18) |
                           ((static_cast<unsigned char>(s[index+1]) & 0x3F) << 12) |
                           ((static_cast<unsigned char>(s[index+2]) & 0x3F) << 6) |
                           (static_cast<unsigned char>(s[index+3]) & 0x3F);
                }
                return b0; // fallback
            }

            // Count code points between byte indices
            Int countbetween(String s, Int start, Int end) {
                Int count = 0;
                Int i = start;
                Int len = static_cast<Int>(s.size());
                while (i < end && i < len) {
                    i += utf8_seq_len(static_cast<unsigned char>(s[i]));
                    ++count;
                }
                return count;
            }

            Boolean hasAtLeast(String s, Int begin, Int end, Int minCount) {
                Int count = 0;
                Int i = begin;
                Int len = static_cast<Int>(s.size());
                while (i < end && i < len) {
                    if (count >= minCount) return true;
                    i += utf8_seq_len(static_cast<unsigned char>(s[i]));
                    ++count;
                }
                return count >= minCount;
            }

            Boolean hasIndex(String s, Int index) {
                return index >= 0 && index < static_cast<Int>(s.size());
            }

            // Advance to next code point
            Int next(String s, Int index) {
                return index + utf8_seq_len(static_cast<unsigned char>(s[index]));
            }

            // Go back to previous code point
            Int prev(String s, Int index) {
                --index;
                while (index > 0 && (static_cast<unsigned char>(s[index]) & 0xC0) == 0x80) {
                    --index;
                }
                return index;
            }

            Int step(String s, Int index, Int by) {
                if (by >= 0) {
                    for (Int i = 0; i < by; ++i) index = next(s, index);
                } else {
                    for (Int i = 0; i > by; --i) index = prev(s, index);
                }
                return index;
            }

            String slice(String s, Int start, Int end) {
                if (start < 0) start = 0;
                if (end > static_cast<Int>(s.size())) end = static_cast<Int>(s.size());
                if (start >= end) return "";
                return s.substr(start, end - start);
            }
            Float64 toFloat64(String s) {
                // Trim whitespace
                size_t start = s.find_first_not_of(" \t\n\r");
                size_t end = s.find_last_not_of(" \t\n\r");
                if (start == std::string::npos) bubble("invalid float string");
                std::string trimmed = s.substr(start, end - start + 1);
                // Check for special values
                if (trimmed == "NaN") return std::numeric_limits<double>::quiet_NaN();
                if (trimmed == "Infinity") return std::numeric_limits<double>::infinity();
                if (trimmed == "-Infinity") return -std::numeric_limits<double>::infinity();
                // Validate format: reject forms like "2.", ".2", "-inf"
                // Must match: optional minus, digits, optional (. digits), optional (e/E optional +/- digits)
                size_t i = 0;
                if (i < trimmed.size() && trimmed[i] == '-') i++;
                if (i >= trimmed.size() || !std::isdigit(trimmed[i])) bubble("invalid float: must start with digit");
                while (i < trimmed.size() && std::isdigit(trimmed[i])) i++;
                if (i < trimmed.size() && trimmed[i] == '.') {
                    i++;
                    if (i >= trimmed.size() || !std::isdigit(trimmed[i])) bubble("invalid float: digit required after decimal point");
                    while (i < trimmed.size() && std::isdigit(trimmed[i])) i++;
                }
                if (i < trimmed.size() && (trimmed[i] == 'e' || trimmed[i] == 'E')) {
                    i++;
                    if (i < trimmed.size() && (trimmed[i] == '+' || trimmed[i] == '-')) i++;
                    if (i >= trimmed.size() || !std::isdigit(trimmed[i])) bubble("invalid float: digit required in exponent");
                    while (i < trimmed.size() && std::isdigit(trimmed[i])) i++;
                }
                if (i != trimmed.size()) bubble("invalid float: trailing characters");
                return std::stod(trimmed);
            }
            Int toInt(String s) {
                // Trim whitespace
                size_t start = 0, end = s.size();
                while (start < end && std::isspace(s[start])) ++start;
                while (end > start && std::isspace(s[end-1])) --end;
                std::string trimmed = s.substr(start, end - start);
                if (trimmed.empty()) bubble<Int>("invalid int: empty string");
                size_t pos = 0;
                Int result = std::stoi(trimmed, &pos);
                if (pos != trimmed.size()) bubble<Int>("invalid int: trailing characters");
                return result;
            }
            Int toInt(String s, Int base) {
                if (base < 2 || base > 36) bubble<Int>("invalid base");
                size_t start = 0, end = s.size();
                while (start < end && std::isspace(s[start])) ++start;
                while (end > start && std::isspace(s[end-1])) --end;
                std::string trimmed = s.substr(start, end - start);
                if (trimmed.empty()) bubble<Int>("invalid int: empty string");
                size_t pos = 0;
                Int result = std::stoi(trimmed, &pos, base);
                if (pos != trimmed.size()) bubble<Int>("invalid int: trailing characters");
                return result;
            }
            Int64 toInt64s(String s) {
                size_t start = 0, end = s.size();
                while (start < end && std::isspace(s[start])) ++start;
                while (end > start && std::isspace(s[end-1])) --end;
                std::string trimmed = s.substr(start, end - start);
                if (trimmed.empty()) bubble<Int64>("invalid int64: empty string");
                size_t pos = 0;
                Int64 result = std::stoll(trimmed, &pos);
                if (pos != trimmed.size()) bubble<Int64>("invalid int64: trailing characters");
                return result;
            }
            Int64 toInt64s(String s, Int base) {
                if (base < 2 || base > 36) bubble<Int64>("invalid base");
                size_t start = 0, end = s.size();
                while (start < end && std::isspace(s[start])) ++start;
                while (end > start && std::isspace(s[end-1])) --end;
                std::string trimmed = s.substr(start, end - start);
                if (trimmed.empty()) bubble<Int64>("invalid int64: empty string");
                size_t pos = 0;
                Int64 result = std::stoll(trimmed, &pos, base);
                if (pos != trimmed.size()) bubble<Int64>("invalid int64: trailing characters");
                return result;
            }
            Int none() { return -1; }
            // StringIndex RTTI: StringIndex >= 0, NoStringIndex == -1
            Boolean is_string_index(StringIndexOption i) { return i >= 0; }
            Boolean is_no_string_index(StringIndexOption i) { return i < 0; }
            StringIndex require_string_index(StringIndexOption i) {
                if (i < 0) bubble<StringIndex>("not a StringIndex");
                return i;
            }
            NoStringIndex require_no_string_index(StringIndexOption i) {
                if (i >= 0) bubble<NoStringIndex>("not a NoStringIndex");
                return i;
            }

            // String indexOf: returns byte position of target in s, or -1 if not found
            StringIndexOption indexOf(String s, String target, StringIndex start = 0) {
                size_t pos = s.find(target, static_cast<size_t>(start));
                if (pos == std::string::npos) return -1;
                return static_cast<StringIndexOption>(pos);
            }

            // String split
            template<class Elem>
            List<Elem> make_list() {
                return std::make_shared<std::vector<Elem>>();
            }

            List<String> split(String s, String delimiter) {
                auto result = make_list<String>();
                size_t pos = 0;
                size_t found;
                while ((found = s.find(delimiter, pos)) != std::string::npos) {
                    result->push_back(s.substr(pos, found - pos));
                    pos = found + delimiter.size();
                }
                result->push_back(s.substr(pos));
                return result;
            }

            // String forEach — iterates over code points, not bytes
            template<class F>
            void foreach(String s, F fn) {
                Int i = 0;
                Int len = static_cast<Int>(s.size());
                while (i < len) {
                    fn(get(s, i));
                    i += utf8_seq_len(static_cast<unsigned char>(s[i]));
                }
            }

            // ==================== StringBuilder ====================
            struct StringBuilder {
                std::ostringstream ss;
                StringBuilder() = default;
            };

            namespace StringBuilderNs {
                Object<StringBuilder> make() {
                    return object<StringBuilder>();
                }
            }

            void append(Object<StringBuilder> sb, String s) {
                sb->ss << s;
            }

            void appendBetween(Object<StringBuilder> sb, String s, Int start, Int end) {
                sb->ss << s.substr(start, end - start);
            }

            void appendCodepoint(Object<StringBuilder> sb, Int cp) {
                // Validate: must be a Unicode scalar value (not surrogate, not > 0x10FFFF)
                if (cp < 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                    bubble<void>("Invalid code point");
                }
                if (cp < 0x80) {
                    sb->ss << static_cast<char>(cp);
                } else if (cp < 0x800) {
                    sb->ss << static_cast<char>(0xC0 | (cp >> 6));
                    sb->ss << static_cast<char>(0x80 | (cp & 0x3F));
                } else if (cp < 0x10000) {
                    sb->ss << static_cast<char>(0xE0 | (cp >> 12));
                    sb->ss << static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
                    sb->ss << static_cast<char>(0x80 | (cp & 0x3F));
                } else {
                    sb->ss << static_cast<char>(0xF0 | (cp >> 18));
                    sb->ss << static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
                    sb->ss << static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
                    sb->ss << static_cast<char>(0x80 | (cp & 0x3F));
                }
            }

            String toString(Object<StringBuilder> sb) {
                return sb->ss.str();
            }

            // ==================== List operations ====================
            // Helper to convert a value to Elem type for list construction
            template<class Elem, class Arg>
            typename std::enable_if<std::is_convertible<Arg, Elem>::value, Elem>::type
            to_elem(Arg&& arg) {
                return static_cast<Elem>(std::forward<Arg>(arg));
            }

            // Specialization for AnyValue: use any_box for non-convertible types
            template<class Elem, class Arg>
            typename std::enable_if<
                !std::is_convertible<Arg, Elem>::value &&
                std::is_same<Elem, AnyValue>::value, Elem>::type
            to_elem(Arg&& arg) {
                return any_box(std::forward<Arg>(arg));
            }

            namespace ListNs {
                template<class Elem, class... Args>
                List<Elem> make(Args... args) {
                    auto list = std::make_shared<std::vector<Elem>>();
                    int dummy[] = { (list->push_back(to_elem<Elem>(args)), 0)... };
                    (void) dummy;
                    return list;
                }
            }

            template<class Elem, class... Args>
            List<Elem> list_of(Args&&... args) {
                auto list = std::make_shared<std::vector<Elem>>();
                int dummy[] = { (list->push_back(to_elem<Elem>(std::forward<Args>(args))), 0)... };
                (void) dummy;
                return list;
            }

            template<class Elem>
            Boolean isempty(List<Elem> list) {
                return list->empty();
            }

            template<class Elem>
            Int length(List<Elem> list) {
                return static_cast<Int>(list->size());
            }

            template<class Elem>
            Elem get(List<Elem> list, Int index) {
                return (*list)[index];
            }

            template<class Elem>
            Elem getor(List<Elem> list, Int index, Elem defaultValue) {
                if (index >= 0 && index < static_cast<Int>(list->size())) {
                    return (*list)[index];
                }
                return defaultValue;
            }

            template<class Elem, class F>
            void foreach(List<Elem> list, F fn) {
                for (const auto& elem : *list) {
                    fn(elem);
                }
            }

            template<class Elem>
            List<Elem> toList(List<Elem> list) {
                return std::make_shared<std::vector<Elem>>(*list);
            }

            template<class Elem>
            ListBuilder<Elem> toListBuilder(List<Elem> list) {
                return std::make_shared<std::vector<Elem>>(*list);
            }

            // Listed operations (same as List for now)
            template<class Elem>
            Boolean is_empty(List<Elem> list) {
                return list->empty();
            }

            template<class Elem, class F>
            List<typename std::result_of<F(Elem)>::type> map(List<Elem> list, F fn) {
                using R = typename std::result_of<F(Elem)>::type;
                auto result = std::make_shared<std::vector<R>>();
                for (const auto& elem : *list) {
                    result->push_back(fn(elem));
                }
                return result;
            }

            template<class Elem, class F>
            List<Elem> filter(List<Elem> list, F fn) {
                auto result = std::make_shared<std::vector<Elem>>();
                for (const auto& elem : *list) {
                    if (fn(elem)) {
                        result->push_back(elem);
                    }
                }
                return result;
            }

            template<class Elem, class F>
            List<typename std::result_of<F(Elem)>::type> mapDropping(List<Elem> list, F fn) {
                // mapDropping returns non-null results
                using R = typename std::result_of<F(Elem)>::type;
                auto result = std::make_shared<std::vector<R>>();
                for (const auto& elem : *list) {
                    result->push_back(fn(elem));
                }
                return result;
            }

            template<class Elem>
            String join(List<Elem> list, String separator) {
                std::ostringstream oss;
                bool first = true;
                for (const auto& elem : *list) {
                    if (!first) oss << separator;
                    oss << elem;
                    first = false;
                }
                return oss.str();
            }

            template<class Elem, class Fn>
            String join(List<Elem> list, String separator, Fn converter) {
                std::ostringstream oss;
                bool first = true;
                for (const auto& elem : *list) {
                    if (!first) oss << separator;
                    oss << converter(elem);
                    first = false;
                }
                return oss.str();
            }

            template<class Elem>
            List<Elem> slice(List<Elem> list, Int start, Int end) {
                auto result = std::make_shared<std::vector<Elem>>();
                if (start < 0) start = 0;
                if (end > static_cast<Int>(list->size())) end = static_cast<Int>(list->size());
                for (Int i = start; i < end; ++i) {
                    result->push_back((*list)[i]);
                }
                return result;
            }

            template<class Elem, class F>
            Elem reduce(List<Elem> list, F fn) {
                auto it = list->begin();
                Elem acc = *it;
                ++it;
                for (; it != list->end(); ++it) {
                    acc = fn(acc, *it);
                }
                return acc;
            }

            template<class Elem, class Acc, class F>
            Acc reduce_from(List<Elem> list, Acc init, F fn) {
                Acc acc = init;
                for (const auto& elem : *list) {
                    acc = fn(acc, elem);
                }
                return acc;
            }

            template<class Elem, class F>
            List<Elem> sorted(List<Elem> list, F comparator) {
                auto result = std::make_shared<std::vector<Elem>>(*list);
                std::sort(result->begin(), result->end(), [&comparator](const Elem& a, const Elem& b) {
                    return comparator(a, b) < 0;
                });
                return result;
            }

            // ==================== ListBuilder operations ====================
            namespace ListBuilderNs {
                template<class Elem>
                ListBuilder<Elem> make() {
                    return std::make_shared<std::vector<Elem>>();
                }
            }

            // Use non-deduced context for elem to allow implicit conversions
            // (e.g., string literal to std::string when Elem = std::string)
            template<class T> struct Identity { using type = T; };

            template<class Elem>
            void add(ListBuilder<Elem> list, typename Identity<Elem>::type elem) {
                list->push_back(elem);
            }

            // Insert at index
            template<class Elem>
            void add(ListBuilder<Elem> list, typename Identity<Elem>::type elem, Int index) {
                list->insert(list->begin() + index, elem);
            }

            template<class Elem>
            void addall(ListBuilder<Elem> list, List<Elem> other) {
                list->insert(list->end(), other->begin(), other->end());
            }

            // Insert all at index
            template<class Elem>
            void addall(ListBuilder<Elem> list, List<Elem> other, Int index) {
                list->insert(list->begin() + index, other->begin(), other->end());
            }

            template<class Elem>
            Elem removeLast(ListBuilder<Elem> list) {
                Elem last = list->back();
                list->pop_back();
                return last;
            }

            template<class Elem>
            void reverse(ListBuilder<Elem> list) {
                std::reverse(list->begin(), list->end());
            }

            template<class Elem>
            List<Elem> splice(ListBuilder<Elem> list, Int start, Int deleteCount, List<Elem> items) {
                auto removed = std::make_shared<std::vector<Elem>>(
                    list->begin() + start, list->begin() + start + deleteCount);
                auto it = list->erase(list->begin() + start, list->begin() + start + deleteCount);
                list->insert(it, items->begin(), items->end());
                return removed;
            }

            // Splice without replacement items (delete only)
            template<class Elem>
            List<Elem> splice(ListBuilder<Elem> list, Int start, Int deleteCount) {
                auto removed = std::make_shared<std::vector<Elem>>(
                    list->begin() + start, list->begin() + start + deleteCount);
                list->erase(list->begin() + start, list->begin() + start + deleteCount);
                return removed;
            }

            template<class Elem>
            void set(ListBuilder<Elem> list, Int index, typename Identity<Elem>::type value) {
                (*list)[index] = value;
            }

            template<class Elem, class F>
            void sort(ListBuilder<Elem> list, F comparator) {
                std::sort(list->begin(), list->end(), [&comparator](const Elem& a, const Elem& b) {
                    return comparator(a, b) < 0;
                });
            }

            // ==================== Map operations ====================
            namespace MapNs {
                template<class Key, class Value>
                Map<Key, Value> make() {
                    return std::make_shared<OrderedMap<Key, Value>>();
                }
                // make(pairs) overload is below, after Pair definition
            }

            namespace MapBuilderNs {
                template<class Key, class Value>
                MapBuilder<Key, Value> make() {
                    return std::make_shared<OrderedMap<Key, Value>>();
                }
            }

            template<class Key, class Value>
            Int length(Map<Key, Value> m) {
                return static_cast<Int>(m->data.size());
            }

            template<class Key, class Value>
            Value get(Map<Key, Value> m, typename Identity<Key>::type key) {
                return m->data.at(key);
            }

            template<class Key, class Value>
            Value getor(Map<Key, Value> m, typename Identity<Key>::type key, typename Identity<Value>::type defaultValue) {
                auto it = m->data.find(key);
                if (it != m->data.end()) return it->second;
                return defaultValue;
            }

            template<class Key, class Value>
            Boolean has(Map<Key, Value> m, typename Identity<Key>::type key) {
                return m->data.find(key) != m->data.end();
            }

            template<class Key, class Value>
            List<Key> keys(Map<Key, Value> m) {
                auto result = std::make_shared<std::vector<Key>>();
                for (const auto& k : m->order) {
                    result->push_back(k);
                }
                return result;
            }

            template<class Key, class Value>
            List<Value> values(Map<Key, Value> m) {
                auto result = std::make_shared<std::vector<Value>>();
                for (const auto& k : m->order) {
                    result->push_back(m->data.at(k));
                }
                return result;
            }

            template<class Key, class Value>
            Map<Key, Value> toMap(Map<Key, Value> m) {
                auto result = std::make_shared<OrderedMap<Key, Value>>();
                result->data = m->data;
                result->order = m->order;
                return result;
            }

            template<class Key, class Value>
            MapBuilder<Key, Value> toMapBuilder(Map<Key, Value> m) {
                auto result = std::make_shared<OrderedMap<Key, Value>>();
                result->data = m->data;
                result->order = m->order;
                return result;
            }

            // toList and toListBuilder for maps are defined after Pair (below)

            template<class Key, class Value, class F>
            List<typename std::result_of<F(Key, Value)>::type> toListWith(Map<Key, Value> m, F fn) {
                using R = typename std::result_of<F(Key, Value)>::type;
                auto result = std::make_shared<std::vector<R>>();
                for (const auto& k : m->order) {
                    result->push_back(fn(k, m->data.at(k)));
                }
                return result;
            }

            template<class Key, class Value, class F>
            ListBuilder<typename std::result_of<F(Key, Value)>::type> toListBuilderWith(Map<Key, Value> m, F fn) {
                using R = typename std::result_of<F(Key, Value)>::type;
                auto result = std::make_shared<std::vector<R>>();
                for (const auto& k : m->order) {
                    result->push_back(fn(k, m->data.at(k)));
                }
                return result;
            }

            template<class Key, class Value, class F>
            void forEach(Map<Key, Value> m, F fn) {
                for (const auto& k : m->order) {
                    fn(k, m->data.at(k));
                }
            }

            template<class Key, class Value>
            void set(MapBuilder<Key, Value> m, typename Identity<Key>::type key, typename Identity<Value>::type value) {
                if (m->data.find(key) == m->data.end()) {
                    m->order.push_back(key);
                }
                m->data[key] = value;
            }

            template<class Key, class Value>
            Value remove(MapBuilder<Key, Value> m, typename Identity<Key>::type key) {
                auto it = m->data.find(key);
                if (it == m->data.end()) bubble<Value>("key not found");
                Value result = it->second;
                m->data.erase(it);
                m->order.erase(
                    std::remove(m->order.begin(), m->order.end(), key),
                    m->order.end());
                return result;
            }

            template<class Key, class Value>
            void clear(MapBuilder<Key, Value> m) {
                m->data.clear();
                m->order.clear();
            }

            // ==================== Pair ====================
            template<class A, class B>
            struct Pair {
                A key;
                B value;
                Pair(A a, B b) : key(std::move(a)), value(std::move(b)) {}
            };

            namespace PairNs {
                template<class A, class B>
                Object<Pair<A, B>> make(A a, B b) {
                    return object<Pair<A, B>>(std::move(a), std::move(b));
                }
            }

            // MapNs::make from pairs (needs Pair defined above)
            namespace MapNs {
                template<class Key, class Value>
                Map<Key, Value> make(List<Object<Pair<Key, Value>>> pairs) {
                    auto result = std::make_shared<OrderedMap<Key, Value>>();
                    for (const auto& p : *pairs) {
                        if (result->data.find(p->key) == result->data.end()) {
                            result->order.push_back(p->key);
                        }
                        result->data[p->key] = p->value;
                    }
                    return result;
                }
            }

            // Map toList/toListBuilder (needs Pair defined above)
            template<class Key, class Value>
            List<Object<Pair<Key, Value>>> toList(Map<Key, Value> m) {
                auto result = std::make_shared<std::vector<Object<Pair<Key, Value>>>>();
                for (const auto& k : m->order) {
                    result->push_back(object<Pair<Key, Value>>(k, m->data.at(k)));
                }
                return result;
            }

            template<class Key, class Value>
            ListBuilder<Object<Pair<Key, Value>>> toListBuilder(Map<Key, Value> m) {
                auto result = std::make_shared<std::vector<Object<Pair<Key, Value>>>>();
                for (const auto& k : m->order) {
                    result->push_back(object<Pair<Key, Value>>(k, m->data.at(k)));
                }
                return result;
            }

            // ==================== Deque ====================
            template<class Elem>
            using Deque = std::shared_ptr<std::deque<Elem>>;

            namespace DequeNs {
                template<class Elem>
                Deque<Elem> make() {
                    return std::make_shared<std::deque<Elem>>();
                }
            }

            template<class Elem>
            void add(Deque<Elem> dq, typename Identity<Elem>::type elem) {
                dq->push_back(elem);
            }

            template<class Elem>
            Boolean isEmpty(Deque<Elem> dq) {
                return dq->empty();
            }

            template<class Elem>
            Elem removeFirst(Deque<Elem> dq) {
                Elem front = dq->front();
                dq->pop_front();
                return front;
            }

            // ==================== DenseBitVector ====================
            struct DenseBitVector {
                std::vector<bool> bits;
                DenseBitVector(Int size) : bits(size, false) {}
            };

            namespace DenseBitVectorNs {
                Object<DenseBitVector> make(Int size) {
                    return object<DenseBitVector>(size);
                }
            }

            Boolean get(Object<DenseBitVector> bv, Int index) {
                return bv->bits[index];
            }

            void set(Object<DenseBitVector> bv, Int index, Boolean value) {
                bv->bits[index] = value;
            }

            // ==================== Bubble / Error ====================
            template<class T>
            [[noreturn]] T bubble(String message) {
                throw std::runtime_error(message);
            }

            template<class T>
            [[noreturn]] T bubble() {
                throw std::runtime_error("bubble");
            }

            [[noreturn]] void pure_virtual() {
                throw std::runtime_error("pure virtual call");
            }

            // Checked cast: dynamic_pointer_cast that throws on failure
            template<class Target, class Source>
            std::shared_ptr<Target> checked_cast(const std::shared_ptr<Source>& src) {
                auto result = std::dynamic_pointer_cast<Target>(src);
                if (!result) {
                    throw std::runtime_error("bad cast");
                }
                return result;
            }

            void print(String s) {
                std::cout << s << std::endl;
            }

            // Safe integer division
            // Wrapping integer division/modulo: handles INT_MIN / -1 overflow
            Int div_wrap(Int a, Int b) {
                if (b == -1 && a == std::numeric_limits<Int>::min()) return a; // wrap
                return a / b;
            }
            Int64 div_wrap64(Int64 a, Int64 b) {
                if (b == -1 && a == std::numeric_limits<Int64>::min()) return a;
                return a / b;
            }
            Int mod_wrap(Int a, Int b) {
                if (b == -1 && a == std::numeric_limits<Int>::min()) return 0;
                return a % b;
            }
            Int64 mod_wrap64(Int64 a, Int64 b) {
                if (b == -1 && a == std::numeric_limits<Int64>::min()) return 0;
                return a % b;
            }
            // Safe division/modulo: also checks for division by zero
            Int div_safe(Int a, Int b) {
                if (b == 0) { bubble("division by zero"); }
                return div_wrap(a, b);
            }
            Int64 div_safe64(Int64 a, Int64 b) {
                if (b == 0) { bubble("division by zero"); }
                return div_wrap64(a, b);
            }
            Int mod_safe(Int a, Int b) {
                if (b == 0) { bubble("modulo by zero"); }
                return mod_wrap(a, b);
            }
            Int64 mod_safe64(Int64 a, Int64 b) {
                if (b == 0) { bubble("modulo by zero"); }
                return mod_wrap64(a, b);
            }

            // ==================== Null checks ====================
            template<class T>
            Boolean is_null(T value) { return false; }

            template<class T>
            Boolean is_null(std::shared_ptr<T> value) { return value == nullptr; }

            template<class T>
            Boolean is_null(std::function<T> value) { return !value; }

            template<class T>
            T not_null(T value) { return value; }

            template<class T>
            Boolean is_null(const NullableParam<T>& value) { return !value.has_value; }

            template<class T>
            T not_null(const NullableParam<T>& value) { return value.value; }

            // ==================== Test framework ====================
            template<class T>
            void testBail(T) {
                throw std::runtime_error("test bail");
            }

            // ==================== Listify ====================
            template<class Elem, class... Args>
            List<Elem> make(Args... args) {
                return ListNs::make<Elem>(args...);
            }

            // ==================== TEMPER_VOID ====================
            #define TEMPER_VOID ((void)0)
            #define TEMPER_TYPE(name) 0
        }
    }
}
#endif // TEMPER_CORE_HPP
