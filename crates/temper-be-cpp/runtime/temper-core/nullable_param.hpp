#pragma once
#include <memory>
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

        // A `NullableParam` of what may itself be null, as a generic
        // `T?` gives where `T` is bound to a nullable type (a
        // `NullableParam`, or a class's `shared_ptr`): flat, as Temper's
        // `A??` is `A?`, so it's null exactly when the value inside is,
        // and its value, null or not, converts back without throwing.
        template<class T, class Inner>
        struct FlatNullableParam {
            T value;
            bool has_value;

            FlatNullableParam() : value(), has_value(false) {}

            FlatNullableParam(const FlatNullableParam& other) = default;

            FlatNullableParam(FlatNullableParam&& other) = default;

            FlatNullableParam(const T& v) : value(v), has_value(!Inner::is_null(v)) {}

            FlatNullableParam(T&& v) : value(std::move(v)), has_value(!Inner::is_null(value)) {}

            FlatNullableParam(std::nullptr_t) : value(), has_value(false) {}

            template<
                class U,
                typename std::enable_if<
                    std::is_convertible<U, T>::value
                    && !std::is_same<typename std::decay<U>::type, T>::value
                    && !std::is_same<typename std::decay<U>::type, std::nullptr_t>::value
                    && !std::is_base_of<FlatNullableParam, typename std::decay<U>::type>::value,
                    int
                >::type = 0
            >
            FlatNullableParam(U&& v) : value(std::forward<U>(v)), has_value(!Inner::is_null(value)) {}

            FlatNullableParam& operator=(const FlatNullableParam& other) = default;

            FlatNullableParam& operator=(FlatNullableParam&& other) = default;

            operator T() const {
                return value;
            }

            bool operator==(std::nullptr_t) const {
                return !has_value;
            }

            bool operator!=(std::nullptr_t) const {
                return has_value;
            }

            bool operator==(const T& other) const {
                return value == other;
            }

            bool operator!=(const T& other) const {
                return !(value == other);
            }
        };

        template<class T>
        struct NullableParamIsNull {
            static bool is_null(const NullableParam<T>& v) {
                return !v.has_value;
            }
        };

        template<class T>
        struct SharedPtrIsNull {
            static bool is_null(const std::shared_ptr<T>& v) {
                return v == nullptr;
            }
        };

        template<class T>
        struct NullableParam<NullableParam<T>>
            : FlatNullableParam<NullableParam<T>, NullableParamIsNull<T>> {
            using FlatNullableParam<NullableParam<T>, NullableParamIsNull<T>>::FlatNullableParam;
            using FlatNullableParam<NullableParam<T>, NullableParamIsNull<T>>::operator=;
            using FlatNullableParam<NullableParam<T>, NullableParamIsNull<T>>::operator==;
            using FlatNullableParam<NullableParam<T>, NullableParamIsNull<T>>::operator!=;

            bool operator==(const NullableParam& other) const {
                return this->value == other.value;
            }

            bool operator!=(const NullableParam& other) const {
                return !(*this == other);
            }
        };

        template<class T>
        struct NullableParam<std::shared_ptr<T>>
            : FlatNullableParam<std::shared_ptr<T>, SharedPtrIsNull<T>> {
            using FlatNullableParam<std::shared_ptr<T>, SharedPtrIsNull<T>>::FlatNullableParam;
            using FlatNullableParam<std::shared_ptr<T>, SharedPtrIsNull<T>>::operator=;
            using FlatNullableParam<std::shared_ptr<T>, SharedPtrIsNull<T>>::operator==;
            using FlatNullableParam<std::shared_ptr<T>, SharedPtrIsNull<T>>::operator!=;

            bool operator==(const NullableParam& other) const {
                return this->value == other.value;
            }

            bool operator!=(const NullableParam& other) const {
                return !(*this == other);
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
