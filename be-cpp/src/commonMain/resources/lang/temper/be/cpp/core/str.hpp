#pragma once
#include <atomic>
#include <cstddef>
#include <cstring>
#include <functional>
#include <new>
#include <ostream>
#include <stdexcept>
#include <streambuf>
#include <string>
#if __cplusplus >= 201703L
#include <string_view>
#endif

namespace temper {
    namespace core {

        namespace str_detail {

            // The one allocation behind a Str: its reference count and size, followed
            // directly by its UTF-8 bytes and a NUL, like Rust's Arc<str>.
            struct Rep {
                std::atomic<std::size_t> refs;
                std::size_t size;

                constexpr Rep(std::size_t refs, std::size_t size) noexcept
                    : refs(refs), size(size) {}

                char * chars() noexcept {
                    return reinterpret_cast<char *>(this + 1);
                }
                char const * chars() const noexcept {
                    return reinterpret_cast<char const *>(this + 1);
                }
            };

            // Every empty Str shares this, so making one allocates nothing. Its count is
            // never touched.
            struct EmptyRep {
                Rep rep;
                char nul;
            };

            template<class T = void>
            struct Statics {
                static EmptyRep empty;
            };
            template<class T>
            EmptyRep Statics<T>::empty = {{1, 0}, '\0'};

        }

        // A Temper String: immutable UTF-8 text shared by reference count, so a copy is a
        // count bump, never a copy of the text. Never null; an empty Str allocates nothing.
        //
        // It converts implicitly from std::string and char const *, and to std::string (a
        // copy) and, under C++17, to std::string_view, so hand-written C++ can keep using
        // std::string at the edges. Its read-only members mirror std::string's.
        class Str {
            str_detail::Rep * rep_;

            static str_detail::Rep * empty_rep() noexcept {
                return &str_detail::Statics<>::empty.rep;
            }

            static str_detail::Rep * make_rep(char const * text, std::size_t size) {
                if (size == 0) {
                    return empty_rep();
                }
                void * mem = ::operator new(sizeof(str_detail::Rep) + size + 1);
                str_detail::Rep * rep = new (mem) str_detail::Rep(1, size);
                std::memcpy(rep->chars(), text, size);
                rep->chars()[size] = '\0';
                return rep;
            }

            void retain() const noexcept {
                if (rep_ != empty_rep()) {
                    rep_->refs.fetch_add(1, std::memory_order_relaxed);
                }
            }

            void release() noexcept {
                if (rep_ == empty_rep()) {
                    return;
                }
                // A sole owner frees without the atomic decrement.
                if (rep_->refs.load(std::memory_order_acquire) == 1
                    || rep_->refs.fetch_sub(1, std::memory_order_acq_rel) == 1) {
                    rep_->~Rep();
                    ::operator delete(rep_);
                }
            }

        public:
            typedef char const * const_iterator;

            Str() noexcept
                : rep_(empty_rep()) {}
            Str(char const * text)
                : rep_(make_rep(text, std::strlen(text))) {}
            Str(char const * text, std::size_t size)
                : rep_(make_rep(text, size)) {}
            Str(std::string const & text)
                : rep_(make_rep(text.data(), text.size())) {}
#if __cplusplus >= 201703L
            Str(std::string_view text)
                : rep_(make_rep(text.data(), text.size())) {}
#endif
            Str(Str const & other) noexcept
                : rep_(other.rep_) {
                retain();
            }
            // A moved-from Str is empty, not null.
            Str(Str && other) noexcept
                : rep_(other.rep_) {
                other.rep_ = empty_rep();
            }
            Str & operator=(Str const & other) noexcept {
                other.retain();
                release();
                rep_ = other.rep_;
                return *this;
            }
            Str & operator=(Str && other) noexcept {
                str_detail::Rep * rep = other.rep_;
                other.rep_ = empty_rep();
                release();
                rep_ = rep;
                return *this;
            }
            ~Str() {
                release();
            }

            std::size_t size() const noexcept {
                return rep_->size;
            }
            std::size_t length() const noexcept {
                return rep_->size;
            }
            bool empty() const noexcept {
                return rep_->size == 0;
            }
            // NUL-terminated.
            char const * data() const noexcept {
                return rep_->chars();
            }
            char const * c_str() const noexcept {
                return rep_->chars();
            }
            char operator[](std::size_t i) const noexcept {
                return rep_->chars()[i];
            }
            const_iterator begin() const noexcept {
                return data();
            }
            const_iterator end() const noexcept {
                return data() + size();
            }

            // Where `size` bytes at `text` first occur at or after `pos`, or std::string::npos.
            std::size_t find(char const * text, std::size_t pos, std::size_t size) const noexcept {
                std::size_t len = this->size();
                if (pos > len || size > len - pos) {
                    return std::string::npos;
                }
                if (size == 0) {
                    return pos;
                }
                char const * base = data();
                char const * last = base + (len - size);
                for (char const * at = base + pos; at <= last; ++at) {
                    at = static_cast<char const *>(std::memchr(at, text[0], static_cast<std::size_t>(last - at) + 1));
                    if (at == nullptr) {
                        return std::string::npos;
                    }
                    if (std::memcmp(at + 1, text + 1, size - 1) == 0) {
                        return static_cast<std::size_t>(at - base);
                    }
                }
                return std::string::npos;
            }
            std::size_t find(Str const & text, std::size_t pos = 0) const noexcept {
                return find(text.data(), pos, text.size());
            }
            std::size_t find(std::string const & text, std::size_t pos = 0) const noexcept {
                return find(text.data(), pos, text.size());
            }
            std::size_t find(char const * text, std::size_t pos = 0) const noexcept {
                return find(text, pos, std::strlen(text));
            }
            std::size_t find(char c, std::size_t pos = 0) const noexcept {
                return find(&c, pos, 1);
            }

            // A copy of part of the text, as std::string::substr makes.
            std::string substr(std::size_t pos = 0, std::size_t n = std::string::npos) const {
                if (pos > size()) {
                    throw std::out_of_range("Str::substr");
                }
                std::size_t rest = size() - pos;
                return std::string(data() + pos, n < rest ? n : rest);
            }

            int compare(Str const & other) const noexcept {
                std::size_t a = size();
                std::size_t b = other.size();
                int c = std::memcmp(data(), other.data(), a < b ? a : b);
                return c != 0 ? c : (a < b ? -1 : (a > b ? 1 : 0));
            }

            // A copy of the text.
            std::string str() const {
                return std::string(data(), size());
            }
            operator std::string() const {
                return str();
            }
#if __cplusplus >= 201703L
            std::string_view view() const noexcept {
                return std::string_view(data(), size());
            }
            operator std::string_view() const noexcept {
                return view();
            }
#endif

            // Whether both share one text, so they are equal without comparing bytes.
            bool shares_text_with(Str const & other) const noexcept {
                return rep_ == other.rep_;
            }

            // The bytes [pbase, pptr) a stream has written so far, so a builder's text
            // becomes a Str in one copy instead of two (`str()`'s, then this one's).
            static Str written_to(std::streambuf * buf);
        };

        namespace str_detail {

            inline bool same_bytes(char const * a, std::size_t na, char const * b, std::size_t nb) noexcept {
                return na == nb && std::memcmp(a, b, na) == 0;
            }

            // Reaches streambuf's protected pbase()/pptr() through member pointers named via
            // a derived class, which C++ allows on any streambuf.
            struct StreambufAccess : std::streambuf {
                static char * begin_of(std::streambuf * buf) {
                    return (buf->*(&StreambufAccess::pbase))();
                }
                static char * end_of(std::streambuf * buf) {
                    return (buf->*(&StreambufAccess::pptr))();
                }
            };

        }

        inline Str Str::written_to(std::streambuf * buf) {
            char * begin = str_detail::StreambufAccess::begin_of(buf);
            char * end = str_detail::StreambufAccess::end_of(buf);
            if (begin == nullptr || end <= begin) {
                return Str();
            }
            return Str(begin, static_cast<std::size_t>(end - begin));
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
