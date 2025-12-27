using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Threading;
#if NET6_0_OR_GREATER
using Microsoft.Extensions.Logging;
#endif

namespace TemperLang.Core
{
    public interface ILoggingConsoleFactory
    {
        ILoggingConsole CreateConsole(string categoryName);
    }

    public interface ILoggingConsole
    {
        void Log(string message);
    }

    public static class Logging
    {
        public static ILoggingConsole CreateConsole(
            this ILoggingConsoleFactory factory,
            Type type
        ) => factory.CreateConsole(type.FullName);
    }

    /// <summary>
    /// Provide a way to have default logging available during static init of
    /// a library but still support configuration later.
    /// </summary>
    public class LazyInitLoggingConsoleFactory : ILoggingConsoleFactory
    {
        ILoggingConsoleFactory factory;
        private readonly object lockObject = new object();

        public ILoggingConsole CreateConsole(string categoryName) =>
            new LazyInitLoggingConsole(this, categoryName);

        /// <summary>
        /// Can be set only once.
        /// </summary>
        public ILoggingConsoleFactory Internal
        {
            internal get => Volatile.Read(ref factory);
            set
            {
                lock (lockObject)
                {
                    if (factory != null)
                    {
                        throw new InvalidOperationException();
                    }
                    factory = value;
                }
            }
        }
    }

    public class LazyInitLoggingConsole : ILoggingConsole
    {
        private ILoggingConsole console;
        private readonly string categoryName;
        private readonly LazyInitLoggingConsoleFactory factory;
        private readonly object lockObject = new object();

        public LazyInitLoggingConsole(LazyInitLoggingConsoleFactory factory, string categoryName)
        {
            this.factory = factory;
            this.categoryName = categoryName;
        }

        public void Log(string message)
        {
            // See if we already have a real console.
            var localConsole = Volatile.Read(ref console);
            if (localConsole == null)
            {
                // Try to make one because missing.
                lock (lockObject)
                {
                    if (console == null)
                    {
                        console = factory.Internal?.CreateConsole(categoryName);
                        // Might still be null here, which is why we don't use Lazy.
                        localConsole = console;
                    }
                }
            }
            // Work around it if still missing.
            if (localConsole == null)
            {
                Trace.WriteLine(message);
            }
            else
            {
                localConsole.Log(message);
            }
        }
    }

    public class TraceLoggingConsoleFactory : ILoggingConsoleFactory
    {
        static TraceLoggingConsole console = new TraceLoggingConsole();

        public ILoggingConsole CreateConsole(string categoryName) => console;
    }

    public class TraceLoggingConsole : ILoggingConsole
    {
        public void Log(string message)
        {
            Trace.WriteLine(message);
        }
    }

#if NET6_0_OR_GREATER
    public class StandardLoggingConsoleFactory : ILoggingConsoleFactory
    {
        readonly ILoggerFactory factory;
        readonly ConcurrentDictionary<string, StandardLoggingConsole> consoles =
            new ConcurrentDictionary<string, StandardLoggingConsole>();

        public StandardLoggingConsoleFactory(ILoggerFactory factory)
        {
            this.factory = factory;
        }

        public ILoggingConsole CreateConsole(string categoryName) =>
            // Cache because LoggerFactory also caches in similar circumstances.
            // Though we don't expect repeated calls on same categoryName.
            consoles.GetOrAdd(
                categoryName,
                (key) => new StandardLoggingConsole(factory.CreateLogger(categoryName))
            );
    }

    public class StandardLoggingConsole : ILoggingConsole
    {
        readonly ILogger logger;

        public StandardLoggingConsole(ILogger logger)
        {
            this.logger = logger;
        }

        public void Log(string message)
        {
            logger.LogInformation(message);
        }
    }
#endif
}
