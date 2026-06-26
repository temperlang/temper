#pragma once
#include <cstdint>
#include <iomanip>
#include <memory>
#include <regex>
#include <sstream>
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
                std::string rewritten = rewriteNamedGroups(formatted, names);
                try {
                    return std::make_shared<CompiledRegex>(
                    std::regex(rewritten, std::regex::ECMAScript), std::move(names));
                } catch (const std::regex_error&) {
                    return std::make_shared<CompiledRegex>(
                    std::regex("(?!)", std::regex::ECMAScript), std::vector<std::string>());
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
