#pragma once
#include <memory>
#include <sstream>
#include <string>
#include "temper_bubble.hpp"
#include "base_types.hpp"
#include "string.hpp"

namespace temper {
    namespace core {
        namespace StringBuilder {

            inline std::shared_ptr<std::ostringstream> make() {
                return std::make_shared<std::ostringstream>();
            }

            inline void append(const std::shared_ptr<std::ostringstream>& sb, const Str& s) {
                *sb << s;
            }

            inline void appendBetween(const std::shared_ptr<std::ostringstream>& sb, const Str& s, int32_t start, int32_t end_pos) {
                if (start < 0) {
                    start = 0;
                }
                if (end_pos > static_cast<int32_t>(s.size())) {
                    end_pos = static_cast<int32_t>(s.size());
                }
                if (end_pos <= start) {
                    return;
                }
                sb->write(s.data() + start, end_pos - start);
            }

            inline void appendCodepoint(const std::shared_ptr<std::ostringstream>& sb, int32_t cp) {
                if (!String::isValidCodepoint(cp)) {
                    bubble<void>("Invalid code point");
                }
                char bytes[4];
                sb->write(bytes, String::encodeUtf8(cp, bytes));
            }

            inline Str toString(const std::shared_ptr<std::ostringstream>& sb) {
                return Str::written_to(sb->rdbuf());
            }

            inline void clear(const std::shared_ptr<std::ostringstream>& sb) {
                sb->str("");
                sb->clear();
            }

            // `StringBuilder.end` is a StringIndex (the index just past the
            // content built so far), not the built string. String indices are
            // UTF-8 byte offsets, matching `String::end`.
            inline int32_t end(const std::shared_ptr<std::ostringstream>& sb) {
                return static_cast<int32_t>(sb->str().size());
            }

        }
    }
}
