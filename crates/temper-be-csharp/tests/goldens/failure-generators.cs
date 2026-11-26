using System;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace FailureGenerators;

public static class FailureGeneratorsGlobal
{
    internal static IEnumerable<T> Generated<T>(Func<IEnumerable<T>> make)
    {
        return make();
    }

    internal static IEnumerable<T> Safely<T>(Func<IEnumerable<T>> make)
    {
        return make();
    }

    internal static string Take(IEnumerable<int> g, int n)
    {
        IList<string> taken = new List<string>();
        while (taken.Count < n)
        {
            IGeneratorResult<int> r;
            bool failed = false;
            IFailure? caught = null;
            IGeneratorResult<int>? held = null;
            try
            {
                held = Core.GeneratorNext(g);
            }
            catch (Exception caught1) when (caught1 is IFailure)
            {
                IFailure failure = (IFailure)caught1;
                caught = failure;
                failed = true;
            }
            if (failed)
            {
                IFailure e = caught!;
                string why = e is TooBig ? "too big " + ((TooBig)e).N.ToString(CultureInfo.InvariantCulture) : e.Message;
                return Listed.Join(taken, " ", (string s) => s) + " " + why;
            }
            else
            {
                r = held!;
            }
            if (r is ValueResult<int>)
            {
                taken.Add(((ValueResult<int>)r).Value.ToString(CultureInfo.InvariantCulture));
            }
            else
            {
                taken.Add("done");
                break;
            }
        }
        return Listed.Join(taken, " ", (string s2) => s2);
    }

    internal static IEnumerable<int> UpTo(int limit)
    {
        IEnumerable<int> fnSteps1()
        {
            int i = 1;
            while (true)
            {
                if (i > limit)
                {
                    throw new TooBig(i);
                }
                yield return i;
                i = i + 1;
            }
        }
        IGenerator<int> fn()
        {
            return Core.AdaptGenerator<int>(fnSteps1);
        }
        return Generated((Func<IEnumerable<int>>)fn);
    }

    /// <exception cref="TooBig">Where it fails with TooBig.</exception>
    internal static int Checked(int n, int limit)
    {
        if (n > limit)
        {
            throw new TooBig(n);
        }
        return n;
    }

    internal static IEnumerable<int> Squares(int limit)
    {
        IEnumerable<int> fnSteps2()
        {
            int t1;
            try
            {
                t1 = Checked(50, limit);
            }
            catch (TooBig)
            {
                t1 = 0;
            }
            yield return t1;
            int i = 1;
            while (true)
            {
                yield return Checked(i * i, limit);
                i = i + 1;
            }
        }
        IGenerator<int> fn()
        {
            return Core.AdaptGenerator<int>(fnSteps2);
        }
        return Generated((Func<IEnumerable<int>>)fn);
    }

    internal static IEnumerable<int> Count(int n)
    {
        IEnumerable<int> fnSteps3()
        {
            {
                int i = 1;
                while (i <= n)
                {
                    yield return i;
                    i = i + 1;
                }
            }
        }
        IGenerator<int> fn()
        {
            return Core.AdaptGenerator<int>(fnSteps3);
        }
        return Safely((Func<IEnumerable<int>>)fn);
    }

    internal static string Shown(IGeneratorResult<int> r)
    {
        if (r is ValueResult<int>)
        {
            return ((ValueResult<int>)r).Value.ToString(CultureInfo.InvariantCulture);
        }
        else
        {
            return "done";
        }
    }

    internal static string FirstTwo(IEnumerable<int> g)
    {
        string a = Shown(Core.GeneratorNext(g));
        string b;
        try
        {
            b = Shown(Core.GeneratorNext(g));
        }
        catch (Exception failure1) when (failure1 is IFailure)
        {
            b = "failed";
        }
        return a + " " + b;
    }

    internal static IEnumerable<int> Doubled(IEnumerable<int> inner)
    {
        IEnumerable<int> fnSteps4()
        {
            while (true)
            {
                IGeneratorResult<int> r = Core.GeneratorNext(inner);
                if (r is ValueResult<int>)
                {
                    yield return ((ValueResult<int>)r).Value * 2;
                }
                else
                {
                    break;
                }
            }
        }
        IGenerator<int> fn()
        {
            return Core.AdaptGenerator<int>(fnSteps4);
        }
        return Generated((Func<IEnumerable<int>>)fn);
    }

    static FailureGeneratorsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("FailureGenerators");
        console.Log(Take(UpTo(3), 9));
        IEnumerable<int> one = UpTo(1);
        console.Log(Take(one, 9));
        console.Log(Take(one, 9));
        console.Log(Take(Squares(10), 9));
        console.Log(Take(Count(2), 9));
        console.Log(FirstTwo(Count(1)));
        console.Log(Take(Doubled(UpTo(2)), 9));
        console.Log(Take(Doubled(Count(2)), 9));
    }
}
