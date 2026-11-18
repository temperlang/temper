using System.Collections.Concurrent;
#if NET6_0_OR_GREATER
using Microsoft.Extensions.Logging;
#endif

namespace RootNamespaceSpot.SupportNamespaceSpot
{
    /// <summary>
    /// Only a single Init call of any kind can be made at most once.
    /// </summary>
    public static class Logging
    {
        public static void Init(ILoggingConsoleFactory factory)
        {
            LoggingConsoleFactory.Internal = new WrappedLoggingConsoleFactory(factory);
        }

#if NET6_0_OR_GREATER
        public static void Init(ILoggerFactory factory)
        {
            LoggingConsoleFactory.Internal = new TemperLang.Core.StandardLoggingConsoleFactory(
                factory
            );
        }
#endif

        internal static readonly TemperLang.Core.LazyInitLoggingConsoleFactory LoggingConsoleFactory =
            new TemperLang.Core.LazyInitLoggingConsoleFactory();
    }

    public interface ILoggingConsoleFactory
    {
        ILoggingConsole CreateConsole(string categoryName);
    }

    public interface ILoggingConsole
    {
        void Log(string message);
    }

    class WrappedLoggingConsoleFactory : TemperLang.Core.ILoggingConsoleFactory
    {
        readonly ILoggingConsoleFactory factory;
        readonly ConcurrentDictionary<string, WrappedLoggingConsole> consoles =
            new ConcurrentDictionary<string, WrappedLoggingConsole>();

        public WrappedLoggingConsoleFactory(ILoggingConsoleFactory factory)
        {
            this.factory = factory;
        }

        public TemperLang.Core.ILoggingConsole CreateConsole(string categoryName) =>
            // Cache because LoggerFactory also caches in similar circumstances.
            // Though we don't expect repeated calls on same categoryName.
            consoles.GetOrAdd(
                categoryName,
                (key) => new WrappedLoggingConsole(factory.CreateConsole(categoryName))
            );
    }

    class WrappedLoggingConsole : TemperLang.Core.ILoggingConsole
    {
        private ILoggingConsole console;

        public WrappedLoggingConsole(ILoggingConsole console)
        {
            this.console = console;
        }

        public void Log(string message)
        {
            console.Log(message);
        }
    }
}
