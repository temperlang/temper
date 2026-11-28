#pragma once
#include <cctype>
#include <cerrno>
#include <cmath>
#include <cstdlib>
#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <vector>
#include "temper_panic.hpp"
#include "failure.hpp"
#include "base_types.hpp"

namespace temper {
    namespace core {

        namespace String {

            // <cctype> classifiers have undefined behavior for char values that are
            // negative (bytes >= 0x80 on platforms with signed char, common in UTF-8).
            // Always widen through unsigned char before classifying.
            inline bool isDigitChar(char c) {
                return std::isdigit(static_cast<unsigned char>(c)) != 0;
            }
            inline bool isSpaceChar(char c) {
                return std::isspace(static_cast<unsigned char>(c)) != 0;
            }

            // True for a UTF-8 continuation byte (0x80..0xBF).
            inline bool isContinuationByte(char c) {
                return (static_cast<unsigned char>(c) & 0xC0) == 0x80;
            }

            inline int32_t utf8_seq_len(unsigned char b) {
                if (b < 0x80) {
                    return 1;
                }
                if ((b & 0xE0) == 0xC0) {
                    return 2;
                }
                if ((b & 0xF0) == 0xE0) {
                    return 3;
                }
                if ((b & 0xF8) == 0xF0) {
                    return 4;
                }
                return 1;
            }

            // Writes cp's UTF-8 bytes to out, which has room for 4, and returns how many.
            inline int32_t encodeUtf8(int32_t cp, char* out) {
                if (cp < 0x80) {
                    out[0] = static_cast<char>(cp);
                    return 1;
                }
                if (cp < 0x800) {
                    out[0] = static_cast<char>(0xC0 | (cp >> 6));
                    out[1] = static_cast<char>(0x80 | (cp & 0x3F));
                    return 2;
                }
                if (cp < 0x10000) {
                    out[0] = static_cast<char>(0xE0 | (cp >> 12));
                    out[1] = static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
                    out[2] = static_cast<char>(0x80 | (cp & 0x3F));
                    return 3;
                }
                out[0] = static_cast<char>(0xF0 | (cp >> 18));
                out[1] = static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
                out[2] = static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
                out[3] = static_cast<char>(0x80 | (cp & 0x3F));
                return 4;
            }

            inline bool isValidCodepoint(int32_t cp) {
                return cp >= 0 && cp < 0x110000 && !(cp >= 0xD800 && cp <= 0xDFFF);
            }

            inline CodePointed<Str> fromCodepoint(int32_t cp) {
                if (!isValidCodepoint(cp)) {
                    return unexpected(InvalidCodePoint::make(cp));
                }
                char bytes[4];
                return Str(bytes, static_cast<size_t>(encodeUtf8(cp, bytes)));
            }

            inline CodePointed<Str> fromCodepoints(const std::shared_ptr<std::vector<int32_t>>& codePoints) {
                std::string result;
                for (int32_t cp : *codePoints) {
                    if (!isValidCodepoint(cp)) {
                        return unexpected(InvalidCodePoint::make(cp));
                    }
                    char bytes[4];
                    result.append(bytes, static_cast<size_t>(encodeUtf8(cp, bytes)));
                }
                return Str(result);
            }

            inline bool isEmpty(const Str& s) {
                return s.empty();
            }
            inline int32_t begin() {
                return 0;
            }
            inline int32_t end(const Str& s) {
                return static_cast<int32_t>(s.size());
            }

            inline int32_t get(const Str& s, int32_t index) {
                int32_t len = static_cast<int32_t>(s.size());
                if (index < 0 || index >= len) {
                    panic<int32_t>("string index " + std::to_string(index) + " out of bounds");
                }
                while (index > 0 && (static_cast<unsigned char>(s[index]) & 0xC0) == 0x80) {
                    --index;
                }
                unsigned char b0 = static_cast<unsigned char>(s[index]);
                if (b0 < 0x80) {
                    return b0;
                }
                // A well-formed multi-byte sequence must have every trailing byte be a
                // continuation byte (0x80..0xBF). A truncated or otherwise malformed
                // sequence falls through to the U+FFFD replacement character rather than
                // decoding adjacent bytes into a garbage code point.
                if ((b0 & 0xE0) == 0xC0 && index + 1 < len
                    && isContinuationByte(s[index + 1])) {
                    return ((b0 & 0x1F) << 6)
                    | (static_cast<unsigned char>(s[index + 1]) & 0x3F);
                }
                if ((b0 & 0xF0) == 0xE0 && index + 2 < len
                    && isContinuationByte(s[index + 1])
                    && isContinuationByte(s[index + 2])) {
                    return ((b0 & 0x0F) << 12)
                    | ((static_cast<unsigned char>(s[index + 1]) & 0x3F) << 6)
                    | (static_cast<unsigned char>(s[index + 2]) & 0x3F);
                }
                if ((b0 & 0xF8) == 0xF0 && index + 3 < len
                    && isContinuationByte(s[index + 1])
                    && isContinuationByte(s[index + 2])
                    && isContinuationByte(s[index + 3])) {
                    return ((b0 & 0x07) << 18)
                    | ((static_cast<unsigned char>(s[index + 1]) & 0x3F) << 12)
                    | ((static_cast<unsigned char>(s[index + 2]) & 0x3F) << 6)
                    | (static_cast<unsigned char>(s[index + 3]) & 0x3F);
                }
                return 0xFFFD;
            }

            inline int32_t countBetween(const Str& s, int32_t start, int32_t end_pos) {
                int32_t count = 0;
                int32_t i = start;
                if (i < 0) {
                    i = 0;
                }
                int32_t len = static_cast<int32_t>(s.size());
                while (i < end_pos && i < len) {
                    i = i + utf8_seq_len(static_cast<unsigned char>(s[i]));
                    ++count;
                }
                return count;
            }

            inline bool hasAtLeast(const Str& s, int32_t begin_pos, int32_t end_pos, int32_t minCount) {
                int32_t count = 0;
                int32_t i = begin_pos;
                if (i < 0) {
                    i = 0;
                }
                int32_t len = static_cast<int32_t>(s.size());
                while (i < end_pos && i < len) {
                    if (count >= minCount) {
                        return true;
                    }
                    i = i + utf8_seq_len(static_cast<unsigned char>(s[i]));
                    ++count;
                }
                return count >= minCount;
            }

            inline bool hasIndex(const Str& s, int32_t index) {
                return index >= 0 && index < static_cast<int32_t>(s.size());
            }

            inline int32_t next(const Str& s, int32_t index) {
                int32_t len = static_cast<int32_t>(s.size());
                if (index < 0 || index >= len) {
                    return len;
                }
                return index + utf8_seq_len(static_cast<unsigned char>(s[index]));
            }

            inline int32_t prev(const Str& s, int32_t index) {
                if (index <= 0) {
                    return 0;
                }
                --index;
                while (index > 0 && (static_cast<unsigned char>(s[index]) & 0xC0) == 0x80) {
                    --index;
                }
                return index;
            }

            inline int32_t step(const Str& s, int32_t index, int32_t by) {
                if (by >= 0) {
                    for (int32_t i = 0; i < by; ++i) {
                        index = next(s, index);
                    }
                } else {
                    for (int32_t i = 0; i > by; --i) {
                        index = prev(s, index);
                    }
                }
                return index;
            }

            // `start`/`end_pos` are byte offsets that callers are expected to have placed on
            // UTF-8 code-point boundaries (e.g. via next()/prev()). Out-of-range values are
            // clamped; a mid-sequence offset would yield bytes that are not valid UTF-8.
            // A slice of the whole string shares its text.
            inline Str slice(const Str& s, int32_t start, int32_t end_pos) {
                if (start < 0) {
                    start = 0;
                }
                if (end_pos > static_cast<int32_t>(s.size())) {
                    end_pos = static_cast<int32_t>(s.size());
                }
                if (start >= end_pos) {
                    return Str();
                }
                if (start == 0 && end_pos == static_cast<int32_t>(s.size())) {
                    return s;
                }
                return Str(s.data() + start, static_cast<size_t>(end_pos - start));
            }

            inline int32_t indexOf(const Str& s, const Str& target, int32_t start = 0) {
                // Clamp a negative start to 0; casting it straight to size_t would wrap to a
                // huge offset and make find() report "not found" even when the target is present.
                if (start < 0) {
                    start = 0;
                }
                size_t pos = s.find(target, static_cast<size_t>(start));
                if (pos == std::string::npos) {
                    return -1;
                }
                return static_cast<int32_t>(pos);
            }

            inline std::shared_ptr<std::vector<Str>> split(const Str& s, const Str& delimiter) {
                std::shared_ptr<std::vector<Str>> result = std::make_shared<std::vector<Str>>();
                if (delimiter.empty()) {
                    size_t i = 0;
                    while (i < s.size()) {
                        size_t seqLen = utf8_seq_len(static_cast<unsigned char>(s[i]));
                        if (seqLen > s.size() - i) {
                            seqLen = s.size() - i;
                        }
                        result->push_back(Str(s.data() + i, seqLen));
                        i = i + seqLen;
                    }
                    return result;
                }
                size_t pos = 0;
                size_t found = s.find(delimiter, pos);
                while (found != std::string::npos) {
                    result->push_back(Str(s.data() + pos, found - pos));
                    pos = found + delimiter.size();
                    found = s.find(delimiter, pos);
                }
                result->push_back(pos == 0 ? s : Str(s.data() + pos, s.size() - pos));
                return result;
            }

            template<class F>
            void forEach(const Str& s, F fn) {
                int32_t i = 0;
                int32_t len = static_cast<int32_t>(s.size());
                while (i < len) {
                    fn(get(s, i));
                    i = i + utf8_seq_len(static_cast<unsigned char>(s[i]));
                }
            }

            // The number `s` spells, or a `ParseError`.
            inline Parsed<double> toFloat64(const Str& s) {
                std::string trimmed = s.str();
                size_t start = trimmed.find_first_not_of(" \t\n\r");
                size_t end_pos = trimmed.find_last_not_of(" \t\n\r");
                if (start == std::string::npos) {
                    return parse_error(s, "not a Float64");
                }
                trimmed = trimmed.substr(start, end_pos - start + 1);
                if (trimmed == "NaN") {
                    return std::numeric_limits<double>::quiet_NaN();
                }
                if (trimmed == "Infinity") {
                    return std::numeric_limits<double>::infinity();
                }
                if (trimmed == "-Infinity") {
                    return -std::numeric_limits<double>::infinity();
                }
                size_t i = 0;
                if (i < trimmed.size() && trimmed[i] == '-') {
                    i++;
                }
                if (i >= trimmed.size() || !isDigitChar(trimmed[i])) {
                    return parse_error(s, "not a Float64");
                }
                while (i < trimmed.size() && isDigitChar(trimmed[i])) {
                    i++;
                }
                if (i < trimmed.size() && trimmed[i] == '.') {
                    i++;
                    if (i >= trimmed.size() || !isDigitChar(trimmed[i])) {
                        return parse_error(s, "not a Float64");
                    }
                    while (i < trimmed.size() && isDigitChar(trimmed[i])) {
                        i++;
                    }
                }
                if (i < trimmed.size() && (trimmed[i] == 'e' || trimmed[i] == 'E')) {
                    i++;
                    if (i < trimmed.size() && (trimmed[i] == '+' || trimmed[i] == '-')) {
                        i++;
                    }
                    if (i >= trimmed.size() || !isDigitChar(trimmed[i])) {
                        return parse_error(s, "not a Float64");
                    }
                    while (i < trimmed.size() && isDigitChar(trimmed[i])) {
                        i++;
                    }
                }
                if (i != trimmed.size()) {
                    return parse_error(s, "not a Float64");
                }
                // Not std::stod: it throws out_of_range whenever strtod sets ERANGE, and strtod
                // sets ERANGE for a result that underflows, subnormal or rounded to zero, as well
                // as for one that overflows. The grammar check above leaves only range errors.
                // An underflowing result is the nearest double, as on the other backends.
                // An overflowing one is a `ParseError`, as on py; whether it should be Infinity
                // instead is undecided (#557).
                errno = 0;
                double result = std::strtod(trimmed.c_str(), nullptr);
                if (errno == ERANGE && std::isinf(result)) {
                    return parse_error(s, "not a Float64");
                }
                return result;
            }

            // The integer `s` spells in `base`, around spaces, or null: in
            // `I`'s range, as `std::stoll` reads it.
            template<class I>
            NullableParam<I> parseInt(const Str& s, int32_t base) {
                if (base < 2 || base > 36) {
                    return nullptr;
                }
                size_t start = 0, end_pos = s.size();
                while (start < end_pos && isSpaceChar(s[start])) {
                    ++start;
                }
                while (end_pos > start && isSpaceChar(s[end_pos - 1])) {
                    --end_pos;
                }
                std::string trimmed = s.substr(start, end_pos - start);
                if (trimmed.empty()) {
                    return nullptr;
                }
                try {
                    size_t pos = 0;
                    long long result = std::stoll(trimmed, &pos, base);
                    if (pos != trimmed.size()
                        || result < std::numeric_limits<I>::min()
                        || result > std::numeric_limits<I>::max()) {
                        return nullptr;
                    }
                    return static_cast<I>(result);
                } catch (const std::exception&) {
                    return nullptr;
                }
            }

            inline Parsed<int32_t> toInt32(const Str& s, int32_t base = 10) {
                NullableParam<int32_t> n = parseInt<int32_t>(s, base);
                if (!n.has_value) {
                    return parse_error(s, "not an Int32");
                }
                return n.value;
            }

            inline Parsed<int64_t> toInt64(const Str& s, int32_t base = 10) {
                NullableParam<int64_t> n = parseInt<int64_t>(s, base);
                if (!n.has_value) {
                    return parse_error(s, "not an Int64");
                }
                return n.value;
            }

            inline Str toString(const Str& s) {
                return s;
            }
            inline int32_t none() {
                return -1;
            }
            inline bool isStringIndex(int32_t i) {
                return i >= 0;
            }
            inline bool isNoStringIndex(int32_t i) {
                return i < 0;
            }

            inline int32_t requireStringIndex(int32_t i) {
                if (i < 0) {
                    panic<int32_t>("failed cast: not a StringIndex");
                }
                return i;
            }

            inline int32_t requireNoStringIndex(int32_t i) {
                if (i >= 0) {
                    panic<int32_t>("failed cast: not a NoStringIndex");
                }
                return i;
            }

        }

    }
}
