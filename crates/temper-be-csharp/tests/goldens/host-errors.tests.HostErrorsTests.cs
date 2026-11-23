using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Collections.Generic;
using TemperLang.Core;
using TemperLang.Std.Regex;
using TemperLang.Std.Testing;

namespace HostErrors;

[TestClass]
public class HostErrorsTests
{
    internal static int At(int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return i;
    }

    internal static IList<int> Built()
    {
        IList<int> lb = new List<int>();
        lb.Add(1);
        return lb;
    }

    internal static int Read(IReadOnlyList<int> ls, int i)
    {
        return Listed.Get(ls, At(i));
    }

    internal static int Write(IList<int> lb, int i)
    {
        Listed.Set(lb, At(i), 3);
        return lb.Count;
    }

    internal static int Insert(IList<int> lb, int i)
    {
        Listed.Add(lb, 2, At(i));
        return lb.Count;
    }

    internal static int RemoveLast(IList<int> lb, int i)
    {
        At(i);
        return Listed.RemoveLast(lb);
    }

    internal static int RemoveFirst(Queue<int> d, int i)
    {
        At(i);
        return Listed.RemoveFirst(d);
    }

    internal static int Sum(IReadOnlyList<int> ls, int i)
    {
        At(i);
        return Listed.Reduce(ls, (int a, int b) => a + b);
    }

    internal static int CodeAt(string s, int i)
    {
        At(i);
        return StringUtil.Get(s, s.Length);
    }

    internal static string Replaced(int i)
    {
        At(i);
        return ((IRegexNode)new CodePoints("b")).Compiled().Replace("abc", (Match match) => Listed.Get(Listed.CreateReadOnlyList<string>("x"), i));
    }

    [TestMethod]
    public void ListRead()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = Read(Listed.CreateReadOnlyList<int>(1, 2), 5);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void ListReadAtTheEnd()
    {
        Test test = new Test();
        try
        {
            IReadOnlyList<int> ls = Listed.CreateReadOnlyList<int>(1, 2);
            int r;
            try
            {
                r = Read(ls, ls.Count);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void NegativeIndex()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = Read(Listed.AsReadOnly(Built()), -1);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void Set()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = Write(Built(), 5);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void SetAtTheEnd()
    {
        Test test = new Test();
        try
        {
            IList<int> lb = Built();
            int r;
            try
            {
                r = Write(lb, lb.Count);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void Insert()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = Insert(Built(), 5);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void InsertPastTheEnd()
    {
        Test test = new Test();
        try
        {
            IList<int> lb = Built();
            int r;
            try
            {
                r = Insert(lb, lb.Count + 1);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void InsertAtTheEndAppends()
    {
        Test test = new Test();
        try
        {
            IList<int> lb = Built();
            int n;
            try
            {
                n = Insert(lb, lb.Count);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                n = -1;
            }
            test.Assert(n == 2 && Listed.Get(lb, 1) == 2, () => "didn't append");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void RemoveLast()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = RemoveLast(new List<int>(), 0);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void RemoveFirst()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = RemoveFirst(new Queue<int>(), 0);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void Reduce()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = Sum(Listed.CreateReadOnlyList<int>(), 0);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void StringRead()
    {
        Test test = new Test();
        try
        {
            int r;
            try
            {
                r = CodeAt("ab", 0);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void ReplaceCallback()
    {
        Test test = new Test();
        try
        {
            string r;
            try
            {
                r = Replaced(3);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                r = "";
            }
            test.Assert(r != "", () => "caught");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }
}
