using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Collections.Generic;
using TemperLang.Core;
using TemperLang.Std.Regex;
using TemperLang.Std.Testing;

namespace HostErrors;

[TestClass]
public class HostErrorsTests
{
    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int At(int i)
    {
        if (i == 99)
        {
            throw new Oops();
        }
        return i;
    }

    internal static IList<int> Built()
    {
        IList<int> lb = new List<int>();
        lb.Add(1);
        return lb;
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int Read(IReadOnlyList<int> ls, int i)
    {
        return Listed.Get(ls, At(i));
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int Write(IList<int> lb, int i)
    {
        Listed.Set(lb, At(i), 3);
        return lb.Count;
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int Insert(IList<int> lb, int i)
    {
        Listed.Add(lb, 2, At(i));
        return lb.Count;
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int RemoveLast(IList<int> lb, int i)
    {
        At(i);
        return Listed.RemoveLast(lb);
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int RemoveFirst(Queue<int> d, int i)
    {
        At(i);
        return Listed.RemoveFirst(d);
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int Sum(IReadOnlyList<int> ls, int i)
    {
        At(i);
        return Listed.Reduce(ls, (int a, int b) => a + b);
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int CodeAt(string s, int i)
    {
        At(i);
        return StringUtil.Get(s, s.Length);
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static string Replaced(int i)
    {
        At(i);
        return ((IRegexNode)new CodePoints("b")).Compiled().Replace("abc", (Match match) => Listed.Get(Listed.CreateReadOnlyList<string>("x"), i));
    }

    [TestMethod]
    public void ListRead()
    {
        Test test = new Test();
        global::System.Exception? failure1 = null;
        try
        {
            int r;
            try
            {
                r = Read(Listed.CreateReadOnlyList<int>(1, 2), 5);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e1)
        {
            failure1 = e1;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure1, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void ListReadAtTheEnd()
    {
        Test test = new Test();
        global::System.Exception? failure2 = null;
        try
        {
            IReadOnlyList<int> ls = Listed.CreateReadOnlyList<int>(1, 2);
            int r;
            try
            {
                r = Read(ls, ls.Count);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e2)
        {
            failure2 = e2;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure2, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void NegativeIndex()
    {
        Test test = new Test();
        global::System.Exception? failure3 = null;
        try
        {
            int r;
            try
            {
                r = Read(Listed.AsReadOnly(Built()), -1);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e3)
        {
            failure3 = e3;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure3, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void Set()
    {
        Test test = new Test();
        global::System.Exception? failure4 = null;
        try
        {
            int r;
            try
            {
                r = Write(Built(), 5);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e4)
        {
            failure4 = e4;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure4, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void SetAtTheEnd()
    {
        Test test = new Test();
        global::System.Exception? failure5 = null;
        try
        {
            IList<int> lb = Built();
            int r;
            try
            {
                r = Write(lb, lb.Count);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e5)
        {
            failure5 = e5;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure5, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void Insert()
    {
        Test test = new Test();
        global::System.Exception? failure6 = null;
        try
        {
            int r;
            try
            {
                r = Insert(Built(), 5);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e6)
        {
            failure6 = e6;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure6, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void InsertPastTheEnd()
    {
        Test test = new Test();
        global::System.Exception? failure7 = null;
        try
        {
            IList<int> lb = Built();
            int r;
            try
            {
                r = Insert(lb, lb.Count + 1);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e7)
        {
            failure7 = e7;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure7, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void InsertAtTheEndAppends()
    {
        Test test = new Test();
        global::System.Exception? failure8 = null;
        try
        {
            IList<int> lb = Built();
            int n;
            try
            {
                n = Insert(lb, lb.Count);
            }
            catch (Oops)
            {
                n = -1;
            }
            test.Assert(n == 2 && Listed.Get(lb, 1) == 2, () => "didn't append");
        }
        catch (global::System.Exception e8)
        {
            failure8 = e8;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure8, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void RemoveLast()
    {
        Test test = new Test();
        global::System.Exception? failure9 = null;
        try
        {
            int r;
            try
            {
                r = RemoveLast(new List<int>(), 0);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e9)
        {
            failure9 = e9;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure9, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void RemoveFirst()
    {
        Test test = new Test();
        global::System.Exception? failure10 = null;
        try
        {
            int r;
            try
            {
                r = RemoveFirst(new Queue<int>(), 0);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e10)
        {
            failure10 = e10;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure10, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void Reduce()
    {
        Test test = new Test();
        global::System.Exception? failure11 = null;
        try
        {
            int r;
            try
            {
                r = Sum(Listed.CreateReadOnlyList<int>(), 0);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e11)
        {
            failure11 = e11;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure11, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void StringRead()
    {
        Test test = new Test();
        global::System.Exception? failure12 = null;
        try
        {
            int r;
            try
            {
                r = CodeAt("ab", 0);
            }
            catch (Oops)
            {
                r = -1;
            }
            test.Assert(r != -1, () => "caught");
        }
        catch (global::System.Exception e12)
        {
            failure12 = e12;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure12, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void ReplaceCallback()
    {
        Test test = new Test();
        global::System.Exception? failure13 = null;
        try
        {
            string r;
            try
            {
                r = Replaced(3);
            }
            catch (Oops)
            {
                r = "";
            }
            test.Assert(r != "", () => "caught");
        }
        catch (global::System.Exception e13)
        {
            failure13 = e13;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure13, test.SoftFailToHard);
        }
    }
}
