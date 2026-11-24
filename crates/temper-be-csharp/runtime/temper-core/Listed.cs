using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Collections.ObjectModel;
using System.Linq;

namespace TemperLang.Core
{
    public static class Listed
    {
        public static void Add<T>(this IList<T> list, T item, int? index = null)
        {
            int at = index ?? list.Count;
            if (at < 0 || at > list.Count)
            {
                Core.IndexOutOfBounds(at, list.Count);
            }
            // Swap order.
            list.Insert(at, item);
        }

        public static void AddAll<T>(
            this IList<T> list,
            IEnumerable<T> collection,
            int? index = null
        )
        {
            int ind = index ?? list.Count;
            if (ind < 0 || ind > list.Count)
            {
                Core.IndexOutOfBounds(ind, list.Count);
            }
            if (ind == list.Count)
            {
                // Customize in case `.Add` is more efficient then `.Insert` for
                // some concrete types. Could it ever matter??? Don't know.
                AddAllAtBack(list, collection);
            }
            else if (list is List<T> concreteList)
            {
                concreteList.InsertRange(ind, collection);
            }
            else
            {
                // TODO C# unit test directly on this functionality.
                foreach (T item in collection)
                {
                    list.Insert(ind, item);
                    ind += 1;
                }
            }
        }

        internal static void AddAllAtBack<T>(this IList<T> list, IEnumerable<T> collection)
        {
            if (list is List<T> concreteList)
            {
                concreteList.AddRange(collection);
            }
            else
            {
                foreach (T item in collection)
                {
                    list.Add(item);
                }
            }
        }

        /// <summary>
        /// Wraps in ReadOnlyCollection if the list isn't already an instance of
        /// IReadOnlyList.
        /// </summary>
        public static IReadOnlyList<T> AsReadOnly<T>(this IList<T> list) =>
            // List<T> is also IReadOnlyList<T>.
            list as IReadOnlyList<T>
            ?? new ReadOnlyCollection<T>(list);

        /// <summary>
        /// Returns a new ReadOnlyCollection of the values.
        /// </summary>
        public static IReadOnlyList<T> CreateReadOnlyList<T>(params T[] values) =>
            new ReadOnlyCollection<T>(values);

        /// <summary>
        /// Applied action to each element of list in order.
        /// </summary>
        public static void ForEach<T>(this IReadOnlyList<T> list, Action<T> action)
        {
            foreach (T element in list)
            {
                action(element);
            }
        }

        /// <summary>
        /// The value at <paramref name="index"/>, panicking out of bounds,
        /// where the indexer throws.
        /// </summary>
        public static T Get<T>(this IReadOnlyList<T> list, int index)
        {
            if (index < 0 || index >= list.Count)
            {
                Core.IndexOutOfBounds(index, list.Count);
            }
            return list[index];
        }

        public static T Get<T>(this IList<T> list, int index)
        {
            if (index < 0 || index >= list.Count)
            {
                Core.IndexOutOfBounds(index, list.Count);
            }
            return list[index];
        }

        // Both of the others apply to a List, so neither would be chosen.
        public static T Get<T>(this List<T> list, int index) =>
            Get((IList<T>)list, index);

        /// <summary>
        /// Sets the value at <paramref name="index"/>, panicking out of
        /// bounds, where the indexer throws.
        /// </summary>
        public static void Set<T>(this IList<T> list, int index, T value)
        {
            if (index < 0 || index >= list.Count)
            {
                Core.IndexOutOfBounds(index, list.Count);
            }
            list[index] = value;
        }

        /// <summary>
        /// Reduces from the first value, panicking if there's none, as
        /// reading it does, where <c>Aggregate</c> throws.
        /// </summary>
        public static T Reduce<T>(IEnumerable<T> list, Func<T, T, T> accumulate)
        {
            using (IEnumerator<T> values = list.GetEnumerator())
            {
                if (!values.MoveNext())
                {
                    Core.IndexOutOfBounds(0, 0);
                }
                T result = values.Current;
                while (values.MoveNext())
                {
                    result = accumulate(result, values.Current);
                }
                return result;
            }
        }

        public static T GetOr<T>(this IReadOnlyList<T> list, int index, T alternative)
        {
            if (index < 0 || index >= list.Count)
            {
                return alternative;
            }
            else
            {
                return list[index];
            }
        }

        public static T GetOr<T>(this IList<T> list, int index, T alternative) =>
            list.AsReadOnly().GetOr(index, alternative);

        public static string Join<T>(
            this IEnumerable<T> values,
            string separator,
            Func<T, string> stringify
        ) => string.Join(separator, values.Select(stringify));

        /// <summary>
        /// Reduces from <paramref name="index"/>, none past the end. A
        /// negative index panics as reading there does.
        /// </summary>
        public static TOut ReduceFromIndex<T, TOut>(
            IEnumerable<T> list,
            TOut initial,
            int index,
            Func<TOut, T, TOut> accumulate
        )
        {
            if (index < 0)
            {
                Core.IndexOutOfBounds(index, list.Count());
            }
            return list.Skip(index).Aggregate(initial, accumulate);
        }

        public static T RemoveLast<T>(this IList<T> list)
        {
            if (list.Count == 0)
            {
                Core.IndexOutOfBounds(-1, 0);
            }
            T last = list[list.Count - 1];
            list.RemoveAt(list.Count - 1);
            return last;
        }

        public static void Reverse<T>(this IList<T> list)
        {
            if (list is List<T> concreteList)
            {
                concreteList.Reverse();
            }
            else
            {
                int count = list.Count;
                for (int i = 0; i < count / 2; i += 1)
                {
                    T temp = list[i];
                    list[i] = list[count - i - 1];
                    list[count - i - 1] = temp;
                }
            }
        }

        public static IReadOnlyList<T> Slice<T>(
            this IReadOnlyList<T> list,
            int start,
            int endExclusive
        )
        {
            start = start.Clamp(0, list.Count);
            endExclusive = endExclusive.Clamp(start, list.Count);
            // Don't use Linq's Skip because not sure it's constant time.
            var builder = new List<T>();
            for (int i = start; i < endExclusive; i += 1)
            {
                builder.Add(list[i]);
            }
            return new ReadOnlyCollection<T>(builder);
        }

        public static IReadOnlyList<T> Slice<T>(this IList<T> list, int start, int endExclusive) =>
            list.AsReadOnly().Slice(start, endExclusive);

        /// <summary>
        /// Sorts stably. A comparison that changes the list's length ends
        /// the process, as <c>panic()</c> does, rather than losing what it
        /// added.
        /// </summary>
        public static void Sort<T>(this IList<T> list, Func<T, T, int> compare)
        {
            // TODO Implement stable in place sorting on IList<T>.
            // For now, this is still stable, but unhappily, it allocates.
            // Cache ordered result.
            int count = list.Count;
            var sorted = list.Sorted(compare);
            if (list.Count != count)
            {
                Environment.FailFast("ListBuilder changed length during sort");
            }
            // If this is a plain List<T>, Clear promises to retain Capacity.
            list.Clear();
            AddAllAtBack(list, sorted);
        }

        public static IReadOnlyList<T> Sorted<T>(
            this IList<T> list,
            Func<T, T, int> compare
        )
        {
            return list.AsReadOnly().Sorted(compare);
        }

        public static IReadOnlyList<T> Sorted<T>(
            this List<T> list,
            Func<T, T, int> compare
        )
        {
            return ((IReadOnlyList<T>)list).Sorted(compare);
        }

        public static IReadOnlyList<T> Sorted<T>(
            this IReadOnlyList<T> list,
            Func<T, T, int> compare
        )
        {
            return list.OrderBy(it => it, compare.ToComparer()).ToList();
        }

        public static IReadOnlyList<T> Splice<T>(
            this IList<T> list,
            int? index = null,
            int? removeCount = null,
            IEnumerable<T> newValues = null
        )
        {
            // Find defaults & bounds.
            int ind = (index ?? 0).Clamp(0, list.Count);
            int count = (removeCount ?? list.Count).Clamp(0, list.Count);
            // Given the list itself, its values as they were go in.
            var values = ReferenceEquals(newValues, list)
                ? list.ToList()
                : newValues ?? Enumerable.Empty<T>();
            int endExclusive = (ind + count).Clamp(0, list.Count);
            count = endExclusive - ind;
            // Get result.
            var result = list.Slice(ind, endExclusive);
            // Remove result range.
            if (count > 0)
            {
                if (list is List<T> concreteList)
                {
                    concreteList.RemoveRange(ind, count);
                }
                else
                {
                    // Remove in reverse order in case it's less work.
                    for (int i = endExclusive - 1; i >= ind; i -= 1)
                    {
                        list.RemoveAt(i);
                    }
                }
            }
            // Insert new values.
            list.AddAll(values, ind);
            // Done.
            return result;
        }

        internal static IComparer<T> ToComparer<T>(this Func<T, T, int> compare)
        {
            return Comparer<T>.Create(new Comparison<T>(compare));
        }

        /// <summary>
        /// Always ensures converting to an immutable list. If collection is
        /// already an ImmutableList, doesn't create a new one.
        /// </summary>
        public static IReadOnlyList<T> ToReadOnlyList<T>(this IEnumerable<T> collection) =>
            collection.ToImmutableList();
    }
}
