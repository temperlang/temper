#pragma once
#include <algorithm>
#include <cstdint>
#include <iomanip>
#include <memory>
#include <regex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>
#include "temper_bubble.hpp"
#include "any_value.hpp"
#include "string_builder.hpp"
#include "mapped.hpp"
#include "map.hpp"

namespace temper {
    namespace core {

        namespace Regex {

            struct CompiledRegex : AnyValueBase {
                std::regex re;
                // Names of the capturing groups, in the order they appear in the pattern.
                // groupNames[k] is the name of capture group number k+1 (group 0 is the full
                // match). Used to map std::smatch's numeric groups back to Temper's named ones.
                std::vector<std::string> groupNames;
                CompiledRegex(std::regex r, std::vector<std::string> names)
                : re(std::move(r)), groupNames(std::move(names)) {}
            };

            // std::regex matches bytes, but Temper regexes match code points, and the formatter
            // writes code points past ASCII as UTF-8, raw or as `\xHH` escapes. In a bracket,
            // each of those bytes would be its own item, and a range ending past ASCII is out of
            // order for signed `char` ("Invalid range in bracket expression"). So rewrite each
            // code set reaching past ASCII, each negated set, and `.` into an alternation of an
            // ASCII bracket and UTF-8 byte sequences, each matching exactly one code point.
            namespace CodeSets {
                typedef std::vector<std::pair<int32_t, int32_t>> Ranges;

                const int32_t maxCode = 0x10FFFF;

                inline Ranges normalize(Ranges ranges) {
                    std::sort(ranges.begin(), ranges.end());
                    Ranges out;
                    for (const auto& r : ranges) {
                        if (!out.empty() && r.first <= out.back().second + 1) {
                            out.back().second = std::max(out.back().second, r.second);
                        } else {
                            out.push_back(r);
                        }
                    }
                    return out;
                }

                inline Ranges complement(const Ranges& ranges) {
                    Ranges out;
                    int32_t next = 0;
                    for (const auto& r : normalize(ranges)) {
                        if (r.first > next) {
                            out.push_back(std::make_pair(next, r.first - 1));
                        }
                        next = r.second + 1;
                    }
                    if (next <= maxCode) {
                        out.push_back(std::make_pair(next, maxCode));
                    }
                    return out;
                }

                // `\d`, `\w`, and `\s` as std::regex has them for bytes: ASCII only.
                inline void addClass(char c, Ranges& out) {
                    Ranges ranges;
                    switch (c) {
                        case 'd': case 'D':
                            ranges.push_back(std::make_pair('0', '9'));
                            break;
                        case 'w': case 'W':
                            ranges.push_back(std::make_pair('0', '9'));
                            ranges.push_back(std::make_pair('A', 'Z'));
                            ranges.push_back(std::make_pair('_', '_'));
                            ranges.push_back(std::make_pair('a', 'z'));
                            break;
                        default:
                            ranges.push_back(std::make_pair('\t', '\r'));
                            ranges.push_back(std::make_pair(' ', ' '));
                            break;
                    }
                    if (c >= 'A' && c <= 'Z') {
                        ranges = complement(ranges);
                    }
                    out.insert(out.end(), ranges.begin(), ranges.end());
                }

                inline int hexValue(char c) {
                    if (c >= '0' && c <= '9') return c - '0';
                    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
                    return -1;
                }

                // The byte at `i`, raw or as a `\xHH` escape, or -1 for any other escape.
                inline int readByte(const std::string& pat, size_t& i) {
                    if (pat[i] != '\\') {
                        return static_cast<unsigned char>(pat[i++]);
                    }
                    if (i + 3 < pat.size() && pat[i + 1] == 'x') {
                        int hi = hexValue(pat[i + 2]);
                        int lo = hexValue(pat[i + 3]);
                        if (hi >= 0 && lo >= 0) {
                            i += 4;
                            return hi * 16 + lo;
                        }
                    }
                    return -1;
                }

                // A code point from its first byte and any UTF-8 continuation bytes after `i`.
                inline int32_t readCode(int first, const std::string& pat, size_t& i) {
                    if (first < 0) {
                        throw std::regex_error(std::regex_constants::error_escape);
                    }
                    int extra = first >= 0xF0 ? 3 : first >= 0xE0 ? 2 : first >= 0xC0 ? 1 : 0;
                    int32_t code = first & (extra == 0 ? 0xFF : 0x3F >> extra);
                    for (int k = 0; k < extra; ++k) {
                        int b = i < pat.size() ? readByte(pat, i) : -1;
                        if (b < 0x80 || b > 0xBF) {
                            throw std::regex_error(std::regex_constants::error_collate);
                        }
                        code = (code << 6) | (b & 0x3F);
                    }
                    return code;
                }

                inline void appendHex(std::string& out, int32_t b) {
                    static const char digits[] = "0123456789abcdef";
                    out.append("\\x");
                    out.push_back(digits[(b >> 4) & 0xF]);
                    out.push_back(digits[b & 0xF]);
                }

                inline int encode(int32_t code, int32_t* bytes) {
                    if (code < 0x800) {
                        bytes[0] = 0xC0 | (code >> 6);
                        bytes[1] = 0x80 | (code & 0x3F);
                        return 2;
                    }
                    if (code < 0x10000) {
                        bytes[0] = 0xE0 | (code >> 12);
                        bytes[1] = 0x80 | ((code >> 6) & 0x3F);
                        bytes[2] = 0x80 | (code & 0x3F);
                        return 3;
                    }
                    bytes[0] = 0xF0 | (code >> 18);
                    bytes[1] = 0x80 | ((code >> 12) & 0x3F);
                    bytes[2] = 0x80 | ((code >> 6) & 0x3F);
                    bytes[3] = 0x80 | (code & 0x3F);
                    return 4;
                }

                // Appends `|` and byte sequences matching the UTF-8 of codes `lo` to `hi`, all
                // past ASCII, splitting until each byte position is one contiguous range.
                inline void appendUtf8(int32_t lo, int32_t hi, std::string& out) {
                    for (int32_t bound : {0x7FF, 0xFFFF}) {
                        if (lo <= bound && bound < hi) {
                            appendUtf8(lo, bound, out);
                            appendUtf8(bound + 1, hi, out);
                            return;
                        }
                    }
                    for (int n = 1; n < 4; ++n) {
                        int32_t m = (1 << (6 * n)) - 1;
                        if ((lo & ~m) != (hi & ~m)) {
                            if ((lo & m) != 0) {
                                appendUtf8(lo, lo | m, out);
                                appendUtf8((lo | m) + 1, hi, out);
                                return;
                            }
                            if ((hi & m) != m) {
                                appendUtf8(lo, (hi & ~m) - 1, out);
                                appendUtf8(hi & ~m, hi, out);
                                return;
                            }
                        }
                    }
                    int32_t loBytes[4];
                    int32_t hiBytes[4];
                    int size = encode(lo, loBytes);
                    encode(hi, hiBytes);
                    out.push_back('|');
                    for (int k = 0; k < size; ++k) {
                        if (loBytes[k] == hiBytes[k]) {
                            appendHex(out, loBytes[k]);
                        } else {
                            out.push_back('[');
                            appendHex(out, loBytes[k]);
                            out.push_back('-');
                            appendHex(out, hiBytes[k]);
                            out.push_back(']');
                        }
                    }
                }

                // A group matching one code point in `ranges`.
                inline std::string alternation(const Ranges& ranges) {
                    std::string ascii;
                    std::string rest;
                    for (const auto& r : normalize(ranges)) {
                        if (r.first < 0x80) {
                            appendHex(ascii, r.first);
                            int32_t asciiEnd = std::min(r.second, static_cast<int32_t>(0x7F));
                            if (asciiEnd > r.first) {
                                ascii.push_back('-');
                                appendHex(ascii, asciiEnd);
                            }
                        }
                        if (r.second >= 0x80) {
                            appendUtf8(std::max(r.first, static_cast<int32_t>(0x80)), r.second, rest);
                        }
                    }
                    if (ascii.empty() && rest.empty()) {
                        return "(?!)";
                    }
                    std::string out("(?:");
                    if (!ascii.empty()) {
                        out.append("[").append(ascii).append("]").append(rest);
                    } else {
                        out.append(rest, 1, std::string::npos);
                    }
                    out.append(")");
                    return out;
                }

                // The code set starting at `i`, just past its `[`, leaving `i` past its `]`.
                inline std::string rewriteSet(const std::string& pat, size_t& i) {
                    size_t start = i - 1;
                    bool negated = i < pat.size() && pat[i] == '^';
                    if (negated) {
                        ++i;
                    }
                    Ranges ranges;
                    // Reads one code into `code`, or adds a class escape and returns false.
                    auto readItem = [&](int32_t& code) -> bool {
                        if (pat[i] == '\\' && i + 1 < pat.size()) {
                            char c = pat[i + 1];
                            switch (c) {
                                case 'd': case 'D': case 's': case 'S': case 'w': case 'W':
                                    addClass(c, ranges);
                                    i += 2;
                                    return false;
                                case 'b': code = '\b'; i += 2; return true;
                                case 'f': code = '\f'; i += 2; return true;
                                case 'n': code = '\n'; i += 2; return true;
                                case 'r': code = '\r'; i += 2; return true;
                                case 't': code = '\t'; i += 2; return true;
                                case 'v': code = '\v'; i += 2; return true;
                                case '0': code = 0; i += 2; return true;
                                default: break;
                            }
                            int b = readByte(pat, i);
                            if (b < 0) {
                                // Any other escape is the code after the backslash.
                                b = static_cast<unsigned char>(pat[i + 1]);
                                i += 2;
                            }
                            code = readCode(b, pat, i);
                            return true;
                        }
                        code = readCode(readByte(pat, i), pat, i);
                        return true;
                    };
                    while (true) {
                        if (i >= pat.size()) {
                            throw std::regex_error(std::regex_constants::error_brack);
                        }
                        if (pat[i] == ']') {
                            ++i;
                            break;
                        }
                        int32_t lo = 0;
                        if (!readItem(lo)) {
                            continue;
                        }
                        int32_t hi = lo;
                        if (i + 1 < pat.size() && pat[i] == '-' && pat[i + 1] != ']') {
                            ++i;
                            if (!readItem(hi) || hi < lo) {
                                throw std::regex_error(std::regex_constants::error_range);
                            }
                        }
                        ranges.push_back(std::make_pair(lo, hi));
                    }
                    if (negated) {
                        return alternation(complement(ranges));
                    }
                    for (const auto& r : ranges) {
                        if (r.second >= 0x80) {
                            return alternation(ranges);
                        }
                    }
                    return pat.substr(start, i - start);
                }

                inline std::string rewrite(const std::string& pat) {
                    std::string out;
                    out.reserve(pat.size());
                    size_t i = 0;
                    while (i < pat.size()) {
                        char c = pat[i];
                        if (c == '\\' && i + 1 < pat.size()) {
                            out.append(pat, i, 2);
                            i += 2;
                        } else if (c == '[') {
                            ++i;
                            out.append(rewriteSet(pat, i));
                        } else if (c == '.') {
                            // Anything but a line terminator, as in ECMAScript.
                            static const std::string dot = alternation(complement(Ranges{
                                {'\n', '\n'}, {'\r', '\r'}, {0x2028, 0x2029},
                            }));
                            out.append(dot);
                            ++i;
                        } else {
                            out.push_back(c);
                            ++i;
                        }
                    }
                    return out;
                }
            }

            // libstdc++'s std::regex rejects ECMAScript named-group syntax `(?<name>...)`
            // outright, so the Temper regex formatter's named captures would otherwise fail to
            // compile. Rewrite each `(?<name>` into a plain capturing group `(` and record the
            // names in order: since the formatter emits every other group as non-capturing
            // `(?:...)`, the k-th recorded name corresponds exactly to std::regex capture group
            // number k+1. Escapes and character classes are skipped so a `(?<` appearing there
            // (e.g. as literal text) is not mistaken for a group opener.
            inline std::string rewriteNamedGroups(const std::string& pat, std::vector<std::string>& names) {
                std::string out;
                out.reserve(pat.size());
                bool inClass = false;
                size_t i = 0;
                while (i < pat.size()) {
                    char c = pat[i];
                    if (c == '\\' && i + 1 < pat.size()) {
                        out.push_back(c);
                        out.push_back(pat[i + 1]);
                        i += 2;
                        continue;
                    }
                    if (inClass) {
                        if (c == ']') {
                            inClass = false;
                        }
                        out.push_back(c);
                        i += 1;
                        continue;
                    }
                    if (c == '[') {
                        inClass = true;
                        out.push_back(c);
                        i += 1;
                        continue;
                    }
                    if (c == '(' && i + 2 < pat.size() && pat[i + 1] == '?' && pat[i + 2] == '<'
                        // Exclude lookbehind `(?<=` / `(?<!` (the formatter does not emit these,
                        // but guard so they are never treated as named captures).
                        && !(i + 3 < pat.size() && (pat[i + 3] == '=' || pat[i + 3] == '!'))) {
                        size_t j = i + 3;
                        std::string name;
                        while (j < pat.size() && pat[j] != '>') {
                            name.push_back(pat[j]);
                            ++j;
                        }
                        if (j < pat.size() && pat[j] == '>') {
                            names.push_back(name);
                            out.push_back('(');
                            i = j + 1;
                            continue;
                        }
                    }
                    out.push_back(c);
                    i += 1;
                }
                return out;
            }

            template<class T>
            std::shared_ptr<AnyValueBase> compileFormatted(std::shared_ptr<T>, std::string formatted) {
                std::vector<std::string> names;
                try {
                    std::string rewritten = rewriteNamedGroups(CodeSets::rewrite(formatted), names);
                    return std::make_shared<CompiledRegex>(
                    std::regex(rewritten, std::regex::ECMAScript), std::move(names));
                } catch (const std::regex_error& error) {
                    // Fail loudly, as other backends do, rather than never matching.
                    throw std::runtime_error(
                    std::string("regex failed to compile: ").append(error.what()).append(": ").append(formatted));
                }
            }

            template<class T>
            bool compiledFound(std::shared_ptr<T>, std::shared_ptr<AnyValueBase> compiled, std::string text) {
                std::shared_ptr<CompiledRegex> cr = std::dynamic_pointer_cast<CompiledRegex>(compiled);
                if (cr == nullptr) {
                    return false;
                }
                return std::regex_search(text, cr->re);
            }

            template<class T, class RegexRefsT>
            std::pair<bool, decltype(std::declval<RegexRefsT>().get_match())> compiledFindImpl(
            std::shared_ptr<T>,
            std::shared_ptr<AnyValueBase> compiled,
            std::string text,
            int32_t beginIdx,
            std::shared_ptr<RegexRefsT> regexRefs
            ) {
                std::shared_ptr<CompiledRegex> cr = std::dynamic_pointer_cast<CompiledRegex>(compiled);
                decltype(regexRefs->get_match()) empty_match;
                if (cr == nullptr) {
                    return std::make_pair(false, empty_match);
                }
                // Clamp beginIdx into [0, len] before turning it into an iterator: an
                // out-of-range value (e.g. from arithmetic at a call site) would otherwise make
                // `text.cbegin() + beginIdx` point outside the string, which is undefined.
                int32_t clampLen = static_cast<int32_t>(text.size());
                if (beginIdx < 0) {
                    beginIdx = 0;
                }
                if (beginIdx > clampLen) {
                    beginIdx = clampLen;
                }
                // Search within the original string starting at beginIdx using iterators
                // rather than a substring, so that anchors and look-behind see the real
                // context: `match_prev_avail` (when not at the very start) tells the engine
                // a previous character exists, which keeps `^` from matching at every
                // resumed position and lets `\b` consider the preceding character.
                std::smatch sm;
                std::string::const_iterator searchBegin = text.cbegin() + beginIdx;
                std::regex_constants::match_flag_type flags = std::regex_constants::match_default;
                if (beginIdx > 0) {
                    flags |= std::regex_constants::match_prev_avail;
                }
                if (!std::regex_search(searchBegin, text.cend(), sm, cr->re, flags)) {
                    return std::make_pair(false, empty_match);
                }
                int32_t matchBegin = static_cast<int32_t>(sm.position(0) + beginIdx);
                int32_t matchEnd = static_cast<int32_t>(matchBegin + static_cast<int32_t>(sm.length(0)));
                std::string fullValue = sm.str(0);
                decltype(regexRefs->get_group()) fullGroup = decltype(regexRefs->get_group())::element_type::make(
                std::string("full"), fullValue, matchBegin, matchEnd
                );
                std::shared_ptr<Mapped::Ordered<std::string, decltype(fullGroup)>> groups =
                Map::make<std::string, decltype(fullGroup)>();
                // Map each named capture group back from its numeric std::smatch slot. Group
                // number k+1 corresponds to cr->groupNames[k]. Unmatched optional groups (e.g.
                // the untaken branch of an alternation) are skipped, matching the other backends.
                for (size_t gi = 0; gi < cr->groupNames.size(); ++gi) {
                    size_t groupNum = gi + 1;
                    if (groupNum >= sm.size() || !sm[groupNum].matched) {
                        continue;
                    }
                    const std::string& gname = cr->groupNames[gi];
                    std::string gvalue = sm.str(groupNum);
                    int32_t gBegin = static_cast<int32_t>(sm.position(groupNum) + beginIdx);
                    int32_t gEnd = static_cast<int32_t>(gBegin + static_cast<int32_t>(sm.length(groupNum)));
                    decltype(fullGroup) group = decltype(regexRefs->get_group())::element_type::make(
                    gname, gvalue, gBegin, gEnd
                    );
                    bool isNew = groups->data.find(gname) == groups->data.end();
                    groups->data[gname] = group;
                    if (isNew) {
                        groups->order.push_back(gname);
                    }
                }
                decltype(regexRefs->get_match()) match = decltype(regexRefs->get_match())::element_type::make(fullGroup, groups);
                return std::make_pair(true, match);
            }

            template<class T, class RegexRefsT>
            decltype(std::declval<RegexRefsT>().get_match())
            compiledFind(
            std::shared_ptr<T> self,
            std::shared_ptr<AnyValueBase> compiled,
            std::string text,
            int32_t beginIdx,
            std::shared_ptr<RegexRefsT> regexRefs
            ) {
                std::pair<bool, decltype(regexRefs->get_match())> result =
                compiledFindImpl(self, compiled, text, beginIdx, regexRefs);
                if (!result.first) {
                    throw TemperBubble();
                }
                return result.second;
            }

            template<class T, class FormatFn, class RegexRefsT>
            std::string compiledReplace(
            std::shared_ptr<T> self,
            std::shared_ptr<AnyValueBase> compiled,
            std::string text,
            FormatFn formatFn,
            std::shared_ptr<RegexRefsT> regexRefs
            ) {
                std::shared_ptr<CompiledRegex> cr = std::dynamic_pointer_cast<CompiledRegex>(compiled);
                if (cr == nullptr) {
                    return text;
                }
                std::string result;
                // Track whether any match occurred separately from whether `result` is
                // non-empty: a match at offset 0 replaced with the empty string (a prefix
                // deletion) leaves `result` empty yet must NOT return the original text.
                bool matched = false;
                int32_t begin_pos = 0;
                int32_t keepBegin = 0;
                int32_t textLen = static_cast<int32_t>(text.size());
                while (begin_pos <= textLen) {
                    std::pair<bool, decltype(regexRefs->get_match())> found =
                    compiledFindImpl(self, compiled, text, begin_pos, regexRefs);
                    if (!found.first) {
                        break;
                    }
                    matched = true;
                    decltype(regexRefs->get_match()) match = found.second;
                    decltype(match->get_full()) fullGroup = match->get_full();
                    int32_t mBegin = fullGroup->get_begin();
                    int32_t mEnd = fullGroup->get_end();
                    result.append(text.substr(keepBegin, mBegin - keepBegin));
                    result.append(formatFn(match));
                    keepBegin = mEnd;
                    if (mEnd > begin_pos) {
                        begin_pos = mEnd;
                    } else {
                        begin_pos = begin_pos + 1;
                    }
                }
                if (!matched) {
                    return text;
                }
                if (keepBegin < textLen) {
                    result.append(text.substr(keepBegin));
                }
                return result;
            }

            template<class T, class RegexRefsT>
            std::shared_ptr<std::vector<std::string>> compiledSplit(
            std::shared_ptr<T> self,
            std::shared_ptr<AnyValueBase> compiled,
            std::string text,
            std::shared_ptr<RegexRefsT> regexRefs
            ) {
                std::shared_ptr<CompiledRegex> cr = std::dynamic_pointer_cast<CompiledRegex>(compiled);
                std::shared_ptr<std::vector<std::string>> parts = std::make_shared<std::vector<std::string>>();
                if (cr == nullptr) {
                    parts->push_back(text); return parts;
                }
                int32_t begin_pos = 0;
                int32_t textLen = static_cast<int32_t>(text.size());
                while (begin_pos <= textLen) {
                    std::pair<bool, decltype(regexRefs->get_match())> found =
                    compiledFindImpl(self, compiled, text, begin_pos, regexRefs);
                    if (!found.first) {
                        parts->push_back(text.substr(begin_pos));
                        break;
                    }
                    decltype(regexRefs->get_match()) match = found.second;
                    decltype(match->get_full()) fullGroup = match->get_full();
                    int32_t mBegin = fullGroup->get_begin();
                    int32_t mEnd = fullGroup->get_end();
                    parts->push_back(text.substr(begin_pos, mBegin - begin_pos));
                    if (mEnd == begin_pos) {
                        if (begin_pos < textLen) {
                            parts->push_back(text.substr(begin_pos, 1));
                        }
                        begin_pos = begin_pos + 1;
                    } else {
                        begin_pos = mEnd;
                    }
                }
                return parts;
            }

            template<class T>
            void pushCaptureName(std::shared_ptr<T>, std::shared_ptr<std::ostringstream> out, std::string name) {
                StringBuilder::append(out, std::string("?<").append(name).append(">"));
            }

            template<class T>
            void pushCodeTo(std::shared_ptr<T>, std::shared_ptr<std::ostringstream> out, int32_t code, bool insideCodeSet) {
                (void) insideCodeSet;
                if (code < 0x80) {
                    std::string ch(1, static_cast<char>(code));
                    if (std::string("\\^$.|?*+()[]{}/-").find(ch) != std::string::npos) {
                        StringBuilder::append(out, std::string("\\").append(ch));
                    } else {
                        StringBuilder::append(out, ch);
                    }
                } else {
                    std::string utf8;
                    if (code < 0x800) {
                        utf8.push_back(static_cast<char>(0xC0 | (code >> 6)));
                        utf8.push_back(static_cast<char>(0x80 | (code & 0x3F)));
                    } else if (code < 0x10000) {
                        utf8.push_back(static_cast<char>(0xE0 | (code >> 12)));
                        utf8.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
                        utf8.push_back(static_cast<char>(0x80 | (code & 0x3F)));
                    } else {
                        utf8.push_back(static_cast<char>(0xF0 | (code >> 18)));
                        utf8.push_back(static_cast<char>(0x80 | ((code >> 12) & 0x3F)));
                        utf8.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
                        utf8.push_back(static_cast<char>(0x80 | (code & 0x3F)));
                    }
                    std::ostringstream oss;
                    for (size_t i = 0; i < utf8.size(); ++i) {
                        oss << "\\x" << std::hex << std::setfill('0') << std::setw(2)
                        << static_cast<int>(static_cast<unsigned char>(utf8[i]));
                    }
                    StringBuilder::append(out, oss.str());
                }
            }

        }

    }
}
