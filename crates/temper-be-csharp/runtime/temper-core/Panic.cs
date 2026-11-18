using System;

namespace TemperLang.Core
{
    /// <summary>
    /// A Temper panic, as core's docs say an operation panics and
    /// generated code's <c>panic()</c> does. It's no <see cref="Bubble"/>,
    /// so it passes every <c>orelse</c>; a test framework reports it as
    /// the test's failure, and the tests after it still run; uncaught, it
    /// ends the process, saying why, as on Java, JavaScript, and Python.
    /// </summary>
    public sealed class Panic : Exception
    {
        public Panic(string message) : base(message) { }
    }
}
