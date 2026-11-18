using System;
using System.Collections.Generic;

#if NET6_0_OR_GREATER
#nullable enable
#endif

namespace TemperLang.Core
{
    /// <summary>
    /// A wrapper for a value produced by a <see cref="IGenerator"/> or
    /// an indicator that the sequence is done.
    /// </summary>
    public interface IGeneratorResult<out T>
    {
#if NET6_0_OR_GREATER
        T? Value { get; }
#else
        /// Undefined when `Done`.
        T Value { get; }
#endif
        bool Done { get; }
    }

    public sealed class ValueResult<T> : IGeneratorResult<T>
    {
        private T value;
        public T Value => value;
        public bool Done => false;

        public ValueResult(T value)
        {
            this.value = value;
        }

        override public String ToString()
        {
            return "ValueResult(" + value + ")";
        }
    }

    public sealed class DoneResult<T> : IGeneratorResult<T>
    {
        public static readonly DoneResult<T> Singleton = new DoneResult<T>();

#if NET6_0_OR_GREATER
        public T? Value => default(T?);
#else
        public T Value => default;
#endif
        public bool Done => true;

        private DoneResult()
        {
        }

        override public String ToString()
        {
            return "DoneResult";
        }
    }

    /// <summary>
    /// Bridges <see cref="IGenerator"/> and <see cref="IEnumerable"> so that a
    /// `yield`ing method can produce an output that works once with `foreach`
    /// but which also works as a Temper sequence of <see cref="IGeneratorResult"/>s.
    /// </summary>
    public interface IGenerator<out T> : IEnumerable<T>, IEnumerator<T> { }
}
