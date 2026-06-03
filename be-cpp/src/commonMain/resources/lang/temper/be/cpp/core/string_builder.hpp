#pragma once
#include <memory>
#include <sstream>
#include <string>
#include "base_types.hpp"
#include "string.hpp"

namespace temper {
    namespace core {
        namespace StringBuilder {

            inline std::shared_ptr<std::ostringstream> make() {
                return std::make_shared<std::ostringstream>();
            }

            inline void append(std::shared_ptr<std::ostringstream> sb, std::string s) {
                *sb << s;
            }

            inline void appendBetween(std::shared_ptr<std::ostringstream> sb, std::string s, int32_t start, int32_t end_pos) {
                if (start < 0) {
                    start = 0;
                }
                if (end_pos <= start) {
                    return;
                }
                *sb << s.substr(start, end_pos - start);
            }

            inline void appendCodepoint(std::shared_ptr<std::ostringstream> sb, int32_t cp) {
                if (cp < 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                    bubble<void>("Invalid code point");
                }
                *sb << String::fromCodepoint(cp);
            }

            inline std::string toString(std::shared_ptr<std::ostringstream> sb) {
                return sb->str();
            }

            inline void clear(std::shared_ptr<std::ostringstream> sb) {
                sb->str("");
                sb->clear();
            }

            // `StringBuilder.end` is a StringIndex (the index just past the
            // content built so far), not the built string. String indices are
            // UTF-8 byte offsets, matching `String::end`.
            inline int32_t end(std::shared_ptr<std::ostringstream> sb) {
                return static_cast<int32_t>(sb->str().size());
            }

        }
    }
}
