using System;

namespace TemperLang.Core
{
    /// <summary>
    /// A Temper panic, as core's docs say an operation panics and
    /// generated code's <c>panic()</c> does. It's no declared failure,
    /// so it passes every handler; a test framework reports it as
    /// the test's failure, and the tests after it still run; uncaught, it
    /// ends the process, saying why, as on Java, JavaScript, and Python.
    /// </summary>
    public sealed class Panic : Exception
    {
        public Panic(string message) : base(message) { }

        /// <summary>
        /// A panic in place of <paramref name="cause"/>, its inner
        /// exception: the failure an <c>orelse panic()</c> replaces.
        /// </summary>
        public Panic(string message, Exception cause) : base(message, cause) { }
    }
}
