#pragma once
#include <cmath>
#include <cstdint>
#include <memory>
#include <string>
#include <type_traits>

namespace temper {
    namespace core {

        namespace Compare {

            template<class T>
            bool lt(T a, T b) {
                return a < b;
            }

            template<class T>
            bool le(T a, T b) {
                return a <= b;
            }

            template<class T>
            bool gt(T a, T b) {
                return a > b;
            }

            template<class T>
            bool ge(T a, T b) {
                return a >= b;
            }

            template<class T>
            struct is_shared_ptr_type : std::false_type {};

            template<class T>
            struct is_shared_ptr_type<std::shared_ptr<T>> : std::true_type {};

            template<
            class T,
            class = typename std::enable_if<!is_shared_ptr_type<T>::value>::type
            >
            bool eq(T a, T b) {
                return a == b;
            }

            // dynamic_cast<void*> yields the address of the most-derived object, so identity
            // holds even when a and b point at the same object through different base
            // subobjects (multiple/virtual inheritance) where the raw pointers would otherwise
            // differ. dynamic_cast<void*> is only well-formed for polymorphic types, so this
            // overload is constrained to the case where both pointees have a vtable.
            template<class A, class B>
            typename std::enable_if<
            std::is_polymorphic<A>::value && std::is_polymorphic<B>::value, bool>::type
            eq(std::shared_ptr<A> a, std::shared_ptr<B> b) {
                return dynamic_cast<void*>(a.get()) == dynamic_cast<void*>(b.get());
            }

            // For non-polymorphic pointees dynamic_cast<void*> would not compile, so fall back
            // to comparing the raw addresses. Without a vtable there is no most-derived-object
            // adjustment to make, so plain pointer identity is the correct notion of equality.
            template<class A, class B>
            typename std::enable_if<
            !(std::is_polymorphic<A>::value && std::is_polymorphic<B>::value), bool>::type
            eq(std::shared_ptr<A> a, std::shared_ptr<B> b) {
                return static_cast<const void*>(a.get()) == static_cast<const void*>(b.get());
            }

            inline bool eq(std::string a, const char* b) {
                return a == b;
            }

            inline bool eq(const char* a, std::string b) {
                return a == b;
            }

            template<
            class T,
            class = typename std::enable_if<!is_shared_ptr_type<T>::value>::type
            >
            bool ne(T a, T b) {
                return a != b;
            }

            template<class A, class B>
            typename std::enable_if<
            std::is_polymorphic<A>::value && std::is_polymorphic<B>::value, bool>::type
            ne(std::shared_ptr<A> a, std::shared_ptr<B> b) {
                return dynamic_cast<void*>(a.get()) != dynamic_cast<void*>(b.get());
            }

            template<class A, class B>
            typename std::enable_if<
            !(std::is_polymorphic<A>::value && std::is_polymorphic<B>::value), bool>::type
            ne(std::shared_ptr<A> a, std::shared_ptr<B> b) {
                return static_cast<const void*>(a.get()) != static_cast<const void*>(b.get());
            }

            inline bool ne(std::string a, const char* b) {
                return a != b;
            }

            inline bool ne(const char* a, std::string b) {
                return a != b;
            }

            template<class T>
            int32_t cmp(T a, T b) {
                if (a < b) {
                    return -1;
                }
                if (a > b) {
                    return 1;
                }
                return 0;
            }

            // Non-template overloads for floating point take precedence over the template above
            // for double/float arguments. They impose a total order suitable for std::sort: NaN
            // sorts greatest (and equal to itself) and -0.0 sorts below +0.0. The plain template
            // would report NaN as equal to everything, violating the strict-weak-ordering that
            // std::sort requires (otherwise undefined behaviour). Mirrors Float64::cmp.
            inline int32_t cmp(double a, double b) {
                bool a_nan = std::isnan(a);
                bool b_nan = std::isnan(b);
                if (a_nan || b_nan) {
                    return a_nan == b_nan ? 0 : (a_nan ? 1 : -1);
                }
                if (a == 0.0 && b == 0.0) {
                    bool a_neg = std::signbit(a);
                    bool b_neg = std::signbit(b);
                    if (a_neg != b_neg) {
                        return a_neg ? -1 : 1;
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

            inline int32_t cmp(float a, float b) {
                return cmp(static_cast<double>(a), static_cast<double>(b));
            }

        }

    }
}
