using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;

namespace TemperLang.Core
{
    public static class Listed
    {
        public static void Add<T>(this IList<T> list, T item, int? index = null)
        {
            // Swap order.
            list.Insert(index ?? list.Count, item);
        }

        public static void AddAll<T>(
            this IList<T> list,
            IEnumerable<T> collection,
            int? index = null
        )
        {
            int ind = index ?? list.Count;
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

        public static T RemoveLast<T>(this IList<T> list)
        {
            // Let appropriate exceptions fly.
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

        public static void Sort<T>(this IList<T> list, Func<T, T, int> compare)
        {
            // TODO Implement stable in place sorting on IList<T>.
            // For now, this is still stable, but unhappily, it allocates.
            // Cache ordered result.
            var sorted = list.Sorted(compare);
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
            var values = newValues ?? Enumerable.Empty<T>();
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
        /// Always ensures wrapping in ReadOnlyCollection. If collection is
        /// already an IList, doesn't create a new one.
        /// </summary>
        public static IReadOnlyList<T> ToReadOnlyList<T>(this IEnumerable<T> collection) =>
            collection as ReadOnlyCollection<T>
            ?? new ReadOnlyCollection<T>(collection as IList<T> ?? new List<T>(collection));
    }
}
