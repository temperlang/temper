using System;

namespace TemperLang.Core
{
    /// <summary>
    /// A Temper bubble: a failure that Temper code declares (<c>throws
    /// Bubble</c>) and that <c>orelse</c> handles. Generated code catches
    /// only this, so any other exception, a host failure, passes every
    /// <c>orelse</c> (a panic ends the process). Host code (a library's
    /// <c>@connected</c> code) bubbles by throwing one, as
    /// <c>Core.Bubble()</c> does.
    /// </summary>
    public class Bubble : Exception
    {
        public Bubble() { }

        public Bubble(string message) : base(message) { }

        /// <summary>A bubble for a host failure, its cause.</summary>
        public Bubble(string message, Exception cause) : base(message, cause) { }
    }
}
