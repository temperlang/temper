using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;

namespace TemperLang.Core
{
    public static class Mapped
    {
        /// ToMap does not need to make a copy, if both are immutable
        public static IReadOnlyDictionary<TKey, TValue> ToMap<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary
        ) => dictionary;

        /// ToMap needs to make a copy, the input is mutable
        public static IReadOnlyDictionary<TKey, TValue> ToMap<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) => new ReadOnlyDictionaryWrapper<TKey, TValue>(
            new OrderedDictionary<TKey, TValue>(dictionary.ToList())
        );

        /// Duplicated for IDictionary below.
        #region Static Methods for IReadOnlyDictionary
        public static int Count<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary
        ) => dictionary.Count;

        public static TValue GetOrDefault<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary,
            TKey key,
            TValue fallback
        ) => dictionary.TryGetValue(key, out var value) ? value : fallback;

        public static bool ContainsKey<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary,
            TKey key
        ) => dictionary.ContainsKey(key);

        public static IReadOnlyList<TKey> Keys<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary
        ) => dictionary.Keys.ToList();

        public static IReadOnlyList<TValue> Values<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary
        ) => dictionary.Values.ToList();

        public static IDictionary<TKey, TValue> ToMapBuilder<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary
        ) => new OrderedDictionary<TKey, TValue>(
            dictionary.ToList()
        );

        public static IReadOnlyList<KeyValuePair<TKey, TValue>> ToList<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary
        ) => dictionary.Select(entry => new KeyValuePair<TKey, TValue>(entry.Key, entry.Value)).ToList();

        public static IList<KeyValuePair<TKey, TValue>> ToListBuilder<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary
        ) => dictionary.Select(entry => new KeyValuePair<TKey, TValue>(entry.Key, entry.Value)).ToList();

        public static IReadOnlyList<TReturn> ToListWith<TKey, TValue, TReturn>(
            this IReadOnlyDictionary<TKey, TValue> dictionary,
            Func<TKey, TValue, TReturn> func
        ) => dictionary.Select(entry => func(entry.Key, entry.Value)).ToList();

        public static IList<TReturn> ToListBuilderWith<TKey, TValue, TReturn>(
            this IReadOnlyDictionary<TKey, TValue> dictionary,
            Func<TKey, TValue, TReturn> func
        ) => dictionary.Select(entry => func(entry.Key, entry.Value)).ToList();

        public static void ForEach<TKey, TValue>(
            this IReadOnlyDictionary<TKey, TValue> dictionary,
            Action<TKey, TValue> func
        )
        {
            foreach (KeyValuePair<TKey, TValue> entry in dictionary)
            {
                func(entry.Key, entry.Value);
            }
        }
        #endregion

        /// Duplicated for IReadOnlyDictionary above.
        #region Static Methods for IDictionary
        public static int Count<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) => dictionary.Count;

        public static TValue GetOrDefault<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary,
            TKey key,
            TValue fallback
        ) => dictionary.ContainsKey(key)
            ? dictionary[key]
            : fallback;

        public static bool ContainsKey<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary,
            TKey key
        ) => dictionary.ContainsKey(key);

        public static IReadOnlyList<TKey> Keys<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) => dictionary.Keys.ToList();

        public static IReadOnlyList<TValue> Values<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) => dictionary.Values.ToList();

        public static IDictionary<TKey, TValue> ToMapBuilder<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) => new OrderedDictionary<TKey, TValue>(
            dictionary.ToList()
        );

        public static IReadOnlyList<KeyValuePair<TKey, TValue>> ToList<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) => dictionary.Select(entry => new KeyValuePair<TKey, TValue>(entry.Key, entry.Value)).ToList();

        public static IList<KeyValuePair<TKey, TValue>> ToListBuilder<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) => dictionary.Select(entry => new KeyValuePair<TKey, TValue>(entry.Key, entry.Value)).ToList();

        public static IReadOnlyList<TReturn> ToListWith<TKey, TValue, TReturn>(
            this IDictionary<TKey, TValue> dictionary,
            Func<TKey, TValue, TReturn> func
        ) => dictionary.Select(entry => func(entry.Key, entry.Value)).ToList();

        public static IList<TReturn> ToListBuilderWith<TKey, TValue, TReturn>(
            this IDictionary<TKey, TValue> dictionary,
            Func<TKey, TValue, TReturn> func
        ) => dictionary.Select(entry => func(entry.Key, entry.Value)).ToList();

        public static void ForEach<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary,
            Action<TKey, TValue> func
        )
        {
            foreach (KeyValuePair<TKey, TValue> entry in dictionary)
            {
                func(entry.Key, entry.Value);
            }
        }
        #endregion

        public static IReadOnlyDictionary<TKey, TValue> AsReadOnly<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary
        ) =>
            dictionary as IReadOnlyDictionary<TKey, TValue>
            ?? new ReadOnlyDictionaryWrapper<TKey, TValue>(dictionary);

        /// <summary>
        /// Returns an ordered dictionary that can't be modified, although not
        /// currently with the same properties as ImmutableDictionary from the
        /// standard library.
        /// </summary>
        public static IReadOnlyDictionary<TKey, TValue> ConstructMap<TKey, TValue>(
            this IEnumerable<KeyValuePair<TKey, TValue>> pairs
        ) =>
            new ReadOnlyDictionaryWrapper<TKey, TValue>(new OrderedDictionary<TKey, TValue>(pairs));
    }

    public class ReadOnlyDictionaryWrapper<TKey, TValue> : IReadOnlyDictionary<TKey, TValue>
    {
        protected readonly IDictionary<TKey, TValue> Wrapped;

        public ReadOnlyDictionaryWrapper(IDictionary<TKey, TValue> wrapped)
        {
            this.Wrapped = wrapped;
        }

        public TValue this[TKey key] => Wrapped[key];

        public IEnumerable<TKey> Keys => Wrapped.Keys;

        public IEnumerable<TValue> Values => Wrapped.Values;

        public int Count => Wrapped.Count;

        public bool ContainsKey(TKey key) => Wrapped.ContainsKey(key);

        public IEnumerator<KeyValuePair<TKey, TValue>> GetEnumerator() => Wrapped.GetEnumerator();

        public bool TryGetValue(TKey key, out TValue value) => Wrapped.TryGetValue(key, out value);

        IEnumerator IEnumerable.GetEnumerator() => Wrapped.GetEnumerator();
    }

    /// <summary>
    /// Maintains insertion order. In the current implementation, removal can
    /// lead to wasted memory.
    /// </summary>
    public class OrderedDictionary<TKey, TValue>
        : IReadOnlyDictionary<TKey, TValue>,
            IDictionary<TKey, TValue>
    {
        Dictionary<TKey, int> indices;
        List<KeyValuePair<TKey, TValue>?> pairs;
        KeyCollection<TKey, TValue> keys;
        ValueCollection<TKey, TValue> values;

        public OrderedDictionary() { }

        public OrderedDictionary(IEnumerable<KeyValuePair<TKey, TValue>> pairs)
        {
            if (pairs.Any())
            {
                // Optimistically presume no duplicates.
                ensure();
                foreach (var pair in pairs)
                {
                    addUnchecked(pair.Key, pair.Value);
                }
                // But then check.
                if (indices.Count < this.pairs.Count)
                {
                    throw new ArgumentException();
                }
            }
        }

        void addUnchecked(TKey key, TValue value)
        {
            pairs.Add(new KeyValuePair<TKey, TValue>(key, value));
            indices[key] = pairs.Count - 1;
        }

        void ensure()
        {
            if (indices == null)
            {
                indices = new Dictionary<TKey, int>();
                pairs = new List<KeyValuePair<TKey, TValue>?>();
            }
        }

        IEnumerable<KeyValuePair<TKey, TValue>> iterator()
        {
            if (indices != null)
            {
                foreach (var pair in pairs)
                {
                    if (pair != null)
                    {
                        yield return pair.Value;
                    }
                }
            }
        }

        public bool Remove(TKey key, out TValue value)
        {
            if (indices != null)
            {
                int index;
#if NETCOREAPP2_0_OR_GREATER
                if (indices.Remove(key, out index))
#else
                if (indices.TryGetValue(key, out index))
#endif
                {
#if !NETCOREAPP2_0_OR_GREATER
                    indices.Remove(key);
#endif
                    value = pairs[index].Value.Value;
                    pairs[index] = null;
                    return true;
                }
            }
            value = default;
            return false;
        }

        #region ICollection implementation
        public int Count
        {
            get => indices?.Count ?? 0;
        }

        public bool IsReadOnly => false;

        public void Add(KeyValuePair<TKey, TValue> item) => Add(item.Key, item.Value);

        public void Clear()
        {
            if (indices != null)
            {
                indices.Clear();
                pairs.Clear();
            }
        }

        public bool Contains(KeyValuePair<TKey, TValue> item)
        {
            if (indices != null)
            {
                int index;
                if (indices.TryGetValue(item.Key, out index))
                {
                    EqualityComparer<TValue> defaultComparer = EqualityComparer<TValue>.Default;
                    return defaultComparer.Equals(pairs[index].Value.Value, item.Value);
                }
            }
            return false;
        }

        public void CopyTo(KeyValuePair<TKey, TValue>[] array, int arrayIndex)
        {
            if (indices != null)
            {
                foreach (var pair in pairs)
                {
                    if (pair != null)
                    {
                        array[arrayIndex] = pair.Value;
                        arrayIndex += 1;
                    }
                }
            }
        }

        public bool Remove(KeyValuePair<TKey, TValue> item)
        {
            if (indices != null)
            {
                int index;
                if (indices.TryGetValue(item.Key, out index))
                {
                    EqualityComparer<TValue> defaultComparer = EqualityComparer<TValue>.Default;
                    if (defaultComparer.Equals(pairs[index].Value.Value, item.Value))
                    {
                        pairs[index] = null;
                        indices.Remove(item.Key);
                        return true;
                    }
                }
            }
            return false;
        }
        #endregion

        #region IDictionary implementation
        public TValue this[TKey key]
        {
            get
            {
                if (indices == null)
                {
                    throw new KeyNotFoundException();
                }
                return pairs[indices[key]].Value.Value;
            }
            set
            {
                ensure();
                int index;
                if (indices.TryGetValue(key, out index))
                {
                    pairs[index] = new KeyValuePair<TKey, TValue>(key, value);
                }
                else
                {
                    addUnchecked(key, value);
                }
            }
        }

        public ICollection<TKey> Keys
        {
            get
            {
                if (keys == null)
                {
                    keys = new KeyCollection<TKey, TValue>(this);
                }
                return keys;
            }
        }

        public ICollection<TValue> Values
        {
            get
            {
                if (values == null)
                {
                    values = new ValueCollection<TKey, TValue>(this);
                }
                return values;
            }
        }

        public void Add(TKey key, TValue value)
        {
            ensure();
            if (indices.ContainsKey(key))
            {
                throw new ArgumentException();
            }
            addUnchecked(key, value);
        }

        public bool ContainsKey(TKey key) => indices?.ContainsKey(key) ?? false;

        public bool Remove(TKey key)
        {
            if (indices != null)
            {
                int index;
#if NETCOREAPP2_0_OR_GREATER
                if (indices.Remove(key, out index))
#else
                if (indices.TryGetValue(key, out index))
#endif
                {
#if !NETCOREAPP2_0_OR_GREATER
                    indices.Remove(key);
#endif
                    pairs[index] = null;
                    return true;
                }
            }
            return false;
        }

        public bool TryGetValue(TKey key, out TValue value)
        {
            if (indices != null)
            {
                int index;
                if (indices.TryGetValue(key, out index))
                {
                    value = pairs[index].Value.Value;
                    return true;
                }
            }
            value = default;
            return false;
        }
        #endregion

        #region IEnumerable implementation
        public IEnumerator<KeyValuePair<TKey, TValue>> GetEnumerator()
        {
            return iterator().GetEnumerator();
        }

        IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();
        #endregion

        #region IReadOnlyDictionary implementation
        IEnumerable<TKey> IReadOnlyDictionary<TKey, TValue>.Keys => Keys;

        IEnumerable<TValue> IReadOnlyDictionary<TKey, TValue>.Values => Values;
        #endregion
    }

    sealed class KeyCollection<TKey, TValue> : ICollection<TKey>
    {
        IReadOnlyDictionary<TKey, TValue> source;

        public KeyCollection(IReadOnlyDictionary<TKey, TValue> source)
        {
            this.source = source;
        }

        public int Count => source.Count;

        public bool IsReadOnly => true;

        public void Add(TKey item) => throw new NotSupportedException();

        public void Clear() => throw new NotSupportedException();

        public bool Contains(TKey item) => source.ContainsKey(item);

        public void CopyTo(TKey[] array, int arrayIndex)
        {
            foreach (var pair in source)
            {
                array[arrayIndex] = pair.Key;
                arrayIndex += 1;
            }
        }

        public IEnumerator<TKey> GetEnumerator() =>
            source.Select((pair) => pair.Key).GetEnumerator();

        public bool Remove(TKey item) => throw new NotSupportedException();

        IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();
    }

    sealed class ValueCollection<TKey, TValue> : ICollection<TValue>
    {
        IReadOnlyDictionary<TKey, TValue> source;

        public ValueCollection(IReadOnlyDictionary<TKey, TValue> source)
        {
            this.source = source;
        }

        public int Count => source.Count;

        public bool IsReadOnly => true;

        public void Add(TValue item) => throw new NotSupportedException();

        public void Clear() => throw new NotSupportedException();

        public bool Contains(TValue item)
        {
            EqualityComparer<TValue> defaultComparer = EqualityComparer<TValue>.Default;
            return source.Any((pair) => defaultComparer.Equals(pair.Value, item));
        }

        public void CopyTo(TValue[] array, int arrayIndex)
        {
            foreach (var pair in source)
            {
                array[arrayIndex] = pair.Value;
                arrayIndex += 1;
            }
        }

        public IEnumerator<TValue> GetEnumerator() =>
            source.Select((pair) => pair.Value).GetEnumerator();

        public bool Remove(TValue item) => throw new NotSupportedException();

        IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();
    }
}
