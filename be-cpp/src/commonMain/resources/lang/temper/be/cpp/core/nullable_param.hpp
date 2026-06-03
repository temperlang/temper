#pragma once
#include <type_traits>
#include "temper_bubble.hpp"

namespace temper {
    namespace core {

        template<class T>
        struct NullableParam {
            T value;
            bool has_value;

            NullableParam() : value(), has_value(false) {}

            NullableParam(const NullableParam& other) : value(other.value), has_value(other.has_value) {}

            NullableParam(NullableParam&& other) : value(std::move(other.value)), has_value(other.has_value) {}

            NullableParam(const T& v) : value(v), has_value(true) {}

            NullableParam(T&& v) : value(std::move(v)), has_value(true) {}

            NullableParam(std::nullptr_t) : value(), has_value(false) {}

            template<
            class U,
            typename std::enable_if<
            std::is_convertible<U, T>::value
            && !std::is_same<typename std::decay<U>::type, T>::value
            && !std::is_same<typename std::decay<U>::type, NullableParam<T>>::value,
            int
            >::type = 0
            >
            NullableParam(U&& v) : value(std::forward<U>(v)), has_value(true) {}

            NullableParam& operator=(const NullableParam& other) {
                value = other.value;
                has_value = other.has_value;
                return *this;
            }

            NullableParam& operator=(NullableParam&& other) {
                value = std::move(other.value);
                has_value = other.has_value;
                return *this;
            }

            NullableParam& operator=(const T& v) {
                value = v;
                has_value = true;
                return *this;
            }

            NullableParam& operator=(T&& v) {
                value = std::move(v);
                has_value = true;
                return *this;
            }

            NullableParam& operator=(std::nullptr_t) {
                value = T();
                has_value = false;
                return *this;
            }

            template<
            class U,
            typename std::enable_if<
            std::is_convertible<U, T>::value
            && !std::is_same<typename std::decay<U>::type, T>::value
            && !std::is_same<typename std::decay<U>::type, NullableParam<T>>::value,
            int
            >::type = 0
            >
            NullableParam& operator=(U&& v) {
                value = std::forward<U>(v);
                has_value = true;
                return *this;
            }

            operator T() const {
                if (!has_value) {
                    throw TemperBubble("accessing null NullableParam");
                }
                return value;
            }

            bool operator==(const NullableParam& other) const {
                if (!has_value && !other.has_value) {
                    return true;
                }
                if (!has_value || !other.has_value) {
                    return false;
                }
                return value == other.value;
            }

            bool operator!=(const NullableParam& other) const {
                return !(*this == other);
            }

            bool operator==(const T& other) const {
                if (!has_value) {
                    return false;
                }
                return value == other;
            }

            bool operator!=(const T& other) const {
                return !(*this == other);
            }

            bool operator==(std::nullptr_t) const {
                return !has_value;
            }

            bool operator!=(std::nullptr_t) const {
                return has_value;
            }
        };

        template<class T>
        bool operator==(const T& a, const NullableParam<T>& b) {
            return b == a;
        }

        template<class T>
        bool operator!=(const T& a, const NullableParam<T>& b) {
            return b != a;
        }

    }
}
