using System;
using System.Collections;
using System.Collections.Generic;

#if NET6_0_OR_GREATER
#nullable enable
#endif

namespace TemperLang.Core
{

    /**
     * <summary>
     * Optionals bridge the distinction between <c>Nullable<ValueType></c> and <c>ReferenceType?</c>.
     * </summary>
     *
     * This type is meant to be API compatible with https://dotnet.github.io/dotNext/api/DotNext.Optional-1.html
     * and will hopefully be retired in favour of that when supported versions allow.
     */
    public readonly struct Optional<T> : IStructuralEquatable
    {
        // The default of this struct will have HasValue=false.
        private readonly T value;
        public readonly bool HasValue;

        internal Optional(T value, bool hasValue)
        {
            this.value = value;
            this.HasValue = hasValue;
        }

        public static Optional<T> None => default(Optional<T>);

        public T Value
        {
            get
            {
                if (HasValue)
                {
                    return (T)value;
                }
                else
                {
                    throw new NullReferenceException();
                }
            }
        }

#if NET6_0_OR_GREATER
        public override string? ToString() => HasValue ? value!.ToString() : "<Null>";
#else
        public override string ToString() => HasValue ? value.ToString() : "<Null>";
#endif

#if NET6_0_OR_GREATER
        public override int GetHashCode() => HasValue ? EqualityComparer<T?>.Default.GetHashCode(value!) : 0;
#else
        public override int GetHashCode() => HasValue ? EqualityComparer<T>.Default.GetHashCode(value) : 0;
#endif

        public int GetHashCode(IEqualityComparer comparer) =>
#if NET6_0_OR_GREATER
            HasValue ? comparer.GetHashCode(value!) : 0;
#else
            HasValue ? comparer.GetHashCode(value) : 0;
#endif

        public bool Equals(Optional<T> other) =>
#if NET6_0_OR_GREATER
            HasValue == other.HasValue && (!HasValue || EqualityComparer<T?>.Default.Equals(value, other.value));
#else
            HasValue == other.HasValue && (!HasValue || EqualityComparer<T>.Default.Equals(value, other.value));
#endif

#if NET6_0_OR_GREATER
        public bool Equals(object? other, IEqualityComparer comparer)
#else
        public bool Equals(object other, IEqualityComparer comparer)
#endif
        {
            if (other == null)
            {
                return !HasValue;
            }
            else if (other is Optional<T> optional)
            {
                return HasValue == optional.HasValue && (!HasValue || comparer.Equals(this.value, optional.Value));
            }
            else if (other is T value)
            {
                return HasValue && comparer.Equals(value, other);
            }
            else
            {
                return false;
            }
        }

#if NET6_0_OR_GREATER
        public override bool Equals(object? other) => other switch
        {
            null => !HasValue,
            Optional<T> optional => Equals(optional),
            T value => HasValue && EqualityComparer<T?>.Default.Equals(this.value, value),
            _ => false,
        };
#else
        public override bool Equals(object other)
        {
            if (other == null)
            {
                return !HasValue;
            }
            else if (other is Optional<T> optional)
            {
                return Equals(optional);
            }
            else if (other is T value)
            {
                return HasValue && EqualityComparer<T>.Default.Equals(this.value, value);
            }
            else
            {
                return false;
            }
        }
#endif

        // Allow casting to T explicitly and from T? implicitly
#if NET6_0_OR_GREATER
        public static implicit operator Optional<T>(T? value)
#else
        public static implicit operator Optional<T>(T value)
#endif
            => value != null ? new Optional<T>(value, true) : None;

        public static explicit operator T(in Optional<T> optional) => optional.Value;
    }

    public static class Optional
    {
        public static Optional<T> Of<T>(Nullable<T> value) where T : struct
            => !value.HasValue ? Optional<T>.None : Some(value.Value);

#if NET6_0_OR_GREATER
        public static Optional<T> Of<T>(T? value) where T : class
            => (value is null) ? Optional<T>.None : Some(value!);
#else
        public static Optional<T> Of<T>(T value) where T : class
            => (value is null) ? Optional<T>.None : Some(value);
#endif

        public static Optional<T> None<T>() => Optional<T>.None;

        public static Optional<T> Some<T>(T value) => new Optional<T>(value, true);

        public static Nullable<T> ToNullable<T>(in Optional<T> o)
            where T : struct
            => o.HasValue ? (T?)o.Value : null;

#if NET6_0_OR_GREATER
        public static T? OrNull<T>(this in Optional<T> o) where T : class
#else
        public static T OrNull<T>(this in Optional<T> o) where T : class
#endif
            => o.HasValue ? o.Value : null;
    }
}
