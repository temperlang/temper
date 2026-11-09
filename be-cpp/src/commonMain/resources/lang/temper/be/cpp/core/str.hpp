#pragma once
#include <cstddef>
#include <cstring>
#include <functional>
#include <memory>
#include <ostream>
#include <stdexcept>
#include <sstream>
#include <string>
#if __cplusplus >= 201703L
#include <string_view>
#endif

namespace temper {
    namespace core {

        // A Temper String: immutable UTF-8 text shared by reference count, so a copy is a
        // count bump, never a copy of the text. Never null; an empty Str allocates nothing.
        //
        // It converts implicitly from std::string and char const *, and to std::string (a
        // copy) and, under C++17, to std::string_view, so hand-written C++ can keep using
        // std::string at the edges. Its read-only members mirror std::string's.
        //
        // The text is a std::string const behind a std::shared_ptr. Every empty Str points at
        // one static std::string with no control block, so copying one touches no count.
        class Str {
            typedef std::shared_ptr<std::string const> Ptr;
            Ptr text_;

            static std::string const & empty_text() noexcept {
                static std::string const text;
                return text;
            }
            static Ptr empty_ptr() noexcept {
                return Ptr(Ptr(), &empty_text());
            }
            static Ptr make_ptr(char const * text, std::size_t size) {
                return size == 0 ? empty_ptr() : std::make_shared<std::string const>(text, size);
            }

        public:
            typedef char const * const_iterator;

            Str() noexcept
                : text_(empty_ptr()) {}
            Str(char const * text)
                : text_(make_ptr(text, std::strlen(text))) {}
            Str(char const * text, std::size_t size)
                : text_(make_ptr(text, size)) {}
            Str(std::string const & text)
                : text_(make_ptr(text.data(), text.size())) {}
            // Takes over a temporary's buffer instead of copying it.
            Str(std::string && text)
                : text_(text.empty() ? empty_ptr() : std::make_shared<std::string const>(std::move(text))) {}
#if __cplusplus >= 201703L
            Str(std::string_view text)
                : text_(make_ptr(text.data(), text.size())) {}
#endif
            Str(Str const & other) noexcept = default;
            // A moved-from Str is empty, not null.
            Str(Str && other) noexcept
                : text_(std::move(other.text_)) {
                other.text_ = empty_ptr();
            }
            Str & operator=(Str const & other) noexcept = default;
            Str & operator=(Str && other) noexcept {
                text_.swap(other.text_);
                return *this;
            }

            std::size_t size() const noexcept {
                return text_->size();
            }
            std::size_t length() const noexcept {
                return text_->size();
            }
            bool empty() const noexcept {
                return text_->empty();
            }
            // NUL-terminated.
            char const * data() const noexcept {
                return text_->data();
            }
            char const * c_str() const noexcept {
                return text_->c_str();
            }
            char operator[](std::size_t i) const noexcept {
                return (*text_)[i];
            }
            const_iterator begin() const noexcept {
                return data();
            }
            const_iterator end() const noexcept {
                return data() + size();
            }

            // Where `size` bytes at `text` first occur at or after `pos`, or std::string::npos.
            std::size_t find(char const * text, std::size_t pos, std::size_t size) const noexcept {
                return text_->find(text, pos, size);
            }
            std::size_t find(Str const & text, std::size_t pos = 0) const noexcept {
                return text_->find(*text.text_, pos);
            }
            std::size_t find(std::string const & text, std::size_t pos = 0) const noexcept {
                return text_->find(text, pos);
            }
            std::size_t find(char const * text, std::size_t pos = 0) const noexcept {
                return text_->find(text, pos);
            }
            std::size_t find(char c, std::size_t pos = 0) const noexcept {
                return text_->find(c, pos);
            }

            // A copy of part of the text, as std::string::substr makes.
            std::string substr(std::size_t pos = 0, std::size_t n = std::string::npos) const {
                return text_->substr(pos, n);
            }

            int compare(Str const & other) const noexcept {
                return text_->compare(*other.text_);
            }

            // A copy of the text.
            std::string str() const {
                return *text_;
            }
            operator std::string() const {
                return str();
            }
#if __cplusplus >= 201703L
            std::string_view view() const noexcept {
                return std::string_view(*text_);
            }
            operator std::string_view() const noexcept {
                return view();
            }
#endif

            // Whether both share one text, so they are equal without comparing bytes.
            bool shares_text_with(Str const & other) const noexcept {
                return text_.get() == other.text_.get();
            }

            // The text a string stream has written so far. Its one copy (`str()`'s) becomes
            // the Str's text without a second.
            static Str written_to(std::stringbuf * buf) {
                return Str(buf->str());
            }
        };

        namespace str_detail {

            inline bool same_bytes(char const * a, std::size_t na, char const * b, std::size_t nb) noexcept {
                return na == nb && std::memcmp(a, b, na) == 0;
            }

        }

        inline bool operator==(Str const & a, Str const & b) noexcept {
            return a.shares_text_with(b) || str_detail::same_bytes(a.data(), a.size(), b.data(), b.size());
        }
        inline bool operator==(Str const & a, std::string const & b) noexcept {
            return str_detail::same_bytes(a.data(), a.size(), b.data(), b.size());
        }
        inline bool operator==(std::string const & a, Str const & b) noexcept {
            return b == a;
        }
        inline bool operator==(Str const & a, char const * b) noexcept {
            return str_detail::same_bytes(a.data(), a.size(), b, std::strlen(b));
        }
        inline bool operator==(char const * a, Str const & b) noexcept {
            return b == a;
        }
        inline bool operator!=(Str const & a, Str const & b) noexcept {
            return !(a == b);
        }
        inline bool operator!=(Str const & a, std::string const & b) noexcept {
            return !(a == b);
        }
        inline bool operator!=(std::string const & a, Str const & b) noexcept {
            return !(a == b);
        }
        inline bool operator!=(Str const & a, char const * b) noexcept {
            return !(a == b);
        }
        inline bool operator!=(char const * a, Str const & b) noexcept {
            return !(a == b);
        }
#if __cplusplus >= 201703L
        inline bool operator==(Str const & a, std::string_view b) noexcept {
            return str_detail::same_bytes(a.data(), a.size(), b.data(), b.size());
        }
        inline bool operator==(std::string_view a, Str const & b) noexcept {
            return b == a;
        }
        inline bool operator!=(Str const & a, std::string_view b) noexcept {
            return !(a == b);
        }
        inline bool operator!=(std::string_view a, Str const & b) noexcept {
            return !(a == b);
        }
#endif
        inline bool operator<(Str const & a, Str const & b) noexcept {
            return a.compare(b) < 0;
        }
        inline bool operator>(Str const & a, Str const & b) noexcept {
            return a.compare(b) > 0;
        }
        inline bool operator<=(Str const & a, Str const & b) noexcept {
            return a.compare(b) <= 0;
        }
        inline bool operator>=(Str const & a, Str const & b) noexcept {
            return a.compare(b) >= 0;
        }

        // Concatenation makes a std::string, which the caller may keep changing.
        inline std::string operator+(Str const & a, Str const & b) {
            std::string out;
            out.reserve(a.size() + b.size());
            return out.append(a.data(), a.size()).append(b.data(), b.size());
        }
        inline std::string operator+(Str const & a, std::string const & b) {
            std::string out;
            out.reserve(a.size() + b.size());
            return out.append(a.data(), a.size()).append(b);
        }
        inline std::string operator+(std::string const & a, Str const & b) {
            std::string out;
            out.reserve(a.size() + b.size());
            return out.append(a).append(b.data(), b.size());
        }
        inline std::string operator+(std::string && a, Str const & b) {
            return std::move(a.append(b.data(), b.size()));
        }
        inline std::string operator+(Str const & a, char const * b) {
            return std::string(a.data(), a.size()).append(b);
        }
        inline std::string operator+(char const * a, Str const & b) {
            return std::string(a).append(b.data(), b.size());
        }
        inline std::string operator+(Str const & a, char b) {
            return std::string(a.data(), a.size()).append(1, b);
        }
        inline std::string operator+(char a, Str const & b) {
            return std::string(1, a).append(b.data(), b.size());
        }

        inline std::ostream & operator<<(std::ostream & out, Str const & s) {
            return out.write(s.data(), static_cast<std::streamsize>(s.size()));
        }

    }
}

namespace std {
    template<>
    struct hash<temper::core::Str> {
        std::size_t operator()(temper::core::Str const & s) const noexcept {
#if __cplusplus >= 201703L
            return std::hash<std::string_view>()(s.view());
#else
            // FNV-1a.
            std::size_t h = sizeof(std::size_t) == 8 ? static_cast<std::size_t>(14695981039346656037ULL) : 2166136261U;
            std::size_t const prime = sizeof(std::size_t) == 8 ? static_cast<std::size_t>(1099511628211ULL) : 16777619U;
            for (char c : s) {
                h = (h ^ static_cast<unsigned char>(c)) * prime;
            }
            return h;
#endif
        }
    };
}

// A string literal as a Str, made once on first use, so evaluating it allocates nothing:
// TEMPER_STR("text"), or TEMPER_STR("text", size) for text with a NUL in it.
#define TEMPER_STR(...) \
    ([]() -> ::temper::core::Str const & { static ::temper::core::Str const s(__VA_ARGS__); return s; }())
