#pragma once
#include <cctype>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>
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

            inline std::string fromCodepoint(int32_t cp) {
                if (cp < 0 || cp >= 0x110000 || (cp >= 0xD800 && cp <= 0xDFFF)) {
                    bubble<std::string>("invalid code point");
                }
                std::string result;
                if (cp < 0x80) {
                    result.push_back(static_cast<char>(cp));
                } else if (cp < 0x800) {
                    result.push_back(static_cast<char>(0xC0 | (cp >> 6)));
                    result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                } else if (cp < 0x10000) {
                    result.push_back(static_cast<char>(0xE0 | (cp >> 12)));
                    result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                    result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                } else {
                    result.push_back(static_cast<char>(0xF0 | (cp >> 18)));
                    result.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
                    result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                    result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                }
                return result;
            }

            inline std::string fromCodepoints(std::shared_ptr<std::vector<int32_t>> codePoints) {
                std::string result;
                for (int32_t cp : *codePoints) {
                    result.append(fromCodepoint(cp));
                }
                return result;
            }

            inline bool isEmpty(std::string s) { return s.empty(); }
            inline int32_t begin() { return 0; }
            inline int32_t end(std::string s) { return static_cast<int32_t>(s.size()); }

            inline int32_t get(std::string s, int32_t index) {
                int32_t len = static_cast<int32_t>(s.size());
                if (index < 0 || index >= len) {
                    bubble<int32_t>("string index out of bounds");
                }
                while (index > 0 && (static_cast<unsigned char>(s[index]) & 0xC0) == 0x80) {
                    --index;
                }
                unsigned char b0 = static_cast<unsigned char>(s[index]);
                if (b0 < 0x80) {
                    return b0;
                }
                if ((b0 & 0xE0) == 0xC0 && index + 1 < len) {
                    return ((b0 & 0x1F) << 6)
                    | (static_cast<unsigned char>(s[index + 1]) & 0x3F);
                }
                if ((b0 & 0xF0) == 0xE0 && index + 2 < len) {
                    return ((b0 & 0x0F) << 12)
                    | ((static_cast<unsigned char>(s[index + 1]) & 0x3F) << 6)
                    | (static_cast<unsigned char>(s[index + 2]) & 0x3F);
                }
                if ((b0 & 0xF8) == 0xF0 && index + 3 < len) {
                    return ((b0 & 0x07) << 18)
                    | ((static_cast<unsigned char>(s[index + 1]) & 0x3F) << 12)
                    | ((static_cast<unsigned char>(s[index + 2]) & 0x3F) << 6)
                    | (static_cast<unsigned char>(s[index + 3]) & 0x3F);
                }
                return 0xFFFD;
            }

            inline int32_t countBetween(std::string s, int32_t start, int32_t end_pos) {
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

            inline bool hasAtLeast(std::string s, int32_t begin_pos, int32_t end_pos, int32_t minCount) {
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

            inline bool hasIndex(std::string s, int32_t index) {
                return index >= 0 && index < static_cast<int32_t>(s.size());
            }

            inline int32_t next(std::string s, int32_t index) {
                int32_t len = static_cast<int32_t>(s.size());
                if (index < 0 || index >= len) {
                    return len;
                }
                return index + utf8_seq_len(static_cast<unsigned char>(s[index]));
            }

            inline int32_t prev(std::string s, int32_t index) {
                if (index <= 0) {
                    return 0;
                }
                --index;
                while (index > 0 && (static_cast<unsigned char>(s[index]) & 0xC0) == 0x80) {
                    --index;
                }
                return index;
            }

            inline int32_t step(std::string s, int32_t index, int32_t by) {
                if (by >= 0) {
                    for (int32_t i = 0; i < by; ++i) { index = next(s, index); }
                } else {
                    for (int32_t i = 0; i > by; --i) { index = prev(s, index); }
                }
                return index;
            }

            // `start`/`end_pos` are byte offsets that callers are expected to have placed on
            // UTF-8 code-point boundaries (e.g. via next()/prev()). Out-of-range values are
            // clamped; a mid-sequence offset would yield bytes that are not valid UTF-8.
            inline std::string slice(std::string s, int32_t start, int32_t end_pos) {
                if (start < 0) {
                    start = 0;
                }
                if (end_pos > static_cast<int32_t>(s.size())) {
                    end_pos = static_cast<int32_t>(s.size());
                }
                if (start >= end_pos) {
                    return "";
                }
                return s.substr(start, end_pos - start);
            }

            inline int32_t indexOf(std::string s, std::string target, int32_t start = 0) {
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

            inline std::shared_ptr<std::vector<std::string>> split(std::string s, std::string delimiter) {
                std::shared_ptr<std::vector<std::string>> result = std::make_shared<std::vector<std::string>>();
                if (delimiter.empty()) {
                    size_t i = 0;
                    while (i < s.size()) {
                        size_t seqLen = utf8_seq_len(static_cast<unsigned char>(s[i]));
                        result->push_back(s.substr(i, seqLen));
                        i = i + seqLen;
                    }
                    return result;
                }
                size_t pos = 0;
                size_t found = s.find(delimiter, pos);
                while (found != std::string::npos) {
                    result->push_back(s.substr(pos, found - pos));
                    pos = found + delimiter.size();
                    found = s.find(delimiter, pos);
                }
                result->push_back(s.substr(pos));
                return result;
            }

            template<class F>
            void forEach(std::string s, F fn) {
                int32_t i = 0;
                int32_t len = static_cast<int32_t>(s.size());
                while (i < len) {
                    fn(get(s, i));
                    i = i + utf8_seq_len(static_cast<unsigned char>(s[i]));
                }
            }

            inline double toFloat64(std::string s) {
                size_t start = s.find_first_not_of(" \t\n\r");
                size_t end_pos = s.find_last_not_of(" \t\n\r");
                if (start == std::string::npos) {
                    bubble("invalid float string");
                }
                std::string trimmed = s.substr(start, end_pos - start + 1);
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
                    bubble("invalid float");
                }
                while (i < trimmed.size() && isDigitChar(trimmed[i])) { i++; }
                if (i < trimmed.size() && trimmed[i] == '.') {
                    i++;
                    if (i >= trimmed.size() || !isDigitChar(trimmed[i])) {
                        bubble("invalid float");
                    }
                    while (i < trimmed.size() && isDigitChar(trimmed[i])) { i++; }
                }
                if (i < trimmed.size() && (trimmed[i] == 'e' || trimmed[i] == 'E')) {
                    i++;
                    if (i < trimmed.size() && (trimmed[i] == '+' || trimmed[i] == '-')) {
                        i++;
                    }
                    if (i >= trimmed.size() || !isDigitChar(trimmed[i])) {
                        bubble("invalid float");
                    }
                    while (i < trimmed.size() && isDigitChar(trimmed[i])) { i++; }
                }
                if (i != trimmed.size()) {
                    bubble("invalid float");
                }
                try { return std::stod(trimmed); }
                catch (const TemperBubble&) { throw; }
                catch (const std::exception&) { bubble<double>("invalid float"); }
            }

            inline int32_t toInt32(std::string s) {
                size_t start = 0, end_pos = s.size();
                while (start < end_pos && isSpaceChar(s[start])) { ++start; }
                while (end_pos > start && isSpaceChar(s[end_pos - 1])) { --end_pos; }
                std::string trimmed = s.substr(start, end_pos - start);
                if (trimmed.empty()) {
                    bubble<int32_t>("invalid int");
                }
                try {
                    size_t pos = 0;
                    int32_t result = std::stoi(trimmed, &pos);
                    if (pos != trimmed.size()) {
                        bubble<int32_t>("invalid int");
                    }
                    return result;
                } catch (const TemperBubble&) { throw; }
                catch (const std::exception&) { bubble<int32_t>("invalid int"); }
            }

            inline int32_t toInt32(std::string s, int32_t base) {
                if (base < 2 || base > 36) {
                    bubble<int32_t>("invalid base");
                }
                size_t start = 0, end_pos = s.size();
                while (start < end_pos && isSpaceChar(s[start])) { ++start; }
                while (end_pos > start && isSpaceChar(s[end_pos - 1])) { --end_pos; }
                std::string trimmed = s.substr(start, end_pos - start);
                if (trimmed.empty()) {
                    bubble<int32_t>("invalid int");
                }
                try {
                    size_t pos = 0;
                    int32_t result = std::stoi(trimmed, &pos, base);
                    if (pos != trimmed.size()) {
                        bubble<int32_t>("invalid int");
                    }
                    return result;
                } catch (const TemperBubble&) { throw; }
                catch (const std::exception&) { bubble<int32_t>("invalid int"); }
            }

            inline int64_t toInt64(std::string s) {
                size_t start = 0, end_pos = s.size();
                while (start < end_pos && isSpaceChar(s[start])) { ++start; }
                while (end_pos > start && isSpaceChar(s[end_pos - 1])) { --end_pos; }
                std::string trimmed = s.substr(start, end_pos - start);
                if (trimmed.empty()) {
                    bubble<int64_t>("invalid int64");
                }
                try {
                    size_t pos = 0;
                    int64_t result = std::stoll(trimmed, &pos);
                    if (pos != trimmed.size()) {
                        bubble<int64_t>("invalid int64");
                    }
                    return result;
                } catch (const TemperBubble&) { throw; }
                catch (const std::exception&) { bubble<int64_t>("invalid int64"); }
            }

            inline int64_t toInt64(std::string s, int32_t base) {
                if (base < 2 || base > 36) {
                    bubble<int64_t>("invalid base");
                }
                size_t start = 0, end_pos = s.size();
                while (start < end_pos && isSpaceChar(s[start])) { ++start; }
                while (end_pos > start && isSpaceChar(s[end_pos - 1])) { --end_pos; }
                std::string trimmed = s.substr(start, end_pos - start);
                if (trimmed.empty()) {
                    bubble<int64_t>("invalid int64");
                }
                try {
                    size_t pos = 0;
                    int64_t result = std::stoll(trimmed, &pos, base);
                    if (pos != trimmed.size()) {
                        bubble<int64_t>("invalid int64");
                    }
                    return result;
                } catch (const TemperBubble&) { throw; }
                catch (const std::exception&) { bubble<int64_t>("invalid int64"); }
            }

            inline std::string toString(std::string s) { return s; }
            inline int32_t none() { return -1; }
            inline bool isStringIndex(int32_t i) { return i >= 0; }
            inline bool isNoStringIndex(int32_t i) { return i < 0; }

            inline int32_t requireStringIndex(int32_t i) {
                if (i < 0) {
                    bubble<int32_t>("not a StringIndex");
                }
                return i;
            }

            inline int32_t requireNoStringIndex(int32_t i) {
                if (i >= 0) {
                    bubble<int32_t>("not a NoStringIndex");
                }
                return i;
            }

        }

    }
}
