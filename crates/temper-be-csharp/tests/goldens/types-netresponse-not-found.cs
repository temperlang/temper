using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;
using TemperLang.Std.Net;

namespace NetresponseNotFound;

public static class NetresponseNotFoundGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly string url;

    static NetresponseNotFoundGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("NetresponseNotFound");
        url = "http://127.0.0.1:" + 28461.ToString(CultureInfo.InvariantCulture) + "/missing";
        IEnumerable<Tuple<object?>> fnSteps1()
        {
            {
                Task<INetResponse> promise1;
                INetResponse resp;
                string body;
                Task<string?> promise2;
                string? subject;
                try
                {
                    promise1 = new NetRequest(url).Send();
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                try
                {
                    resp = global::TemperLang.Core.Async.GetPromiseResult(promise1);
                    console.Log("status " + resp.Status.ToString(CultureInfo.InvariantCulture));
                    promise2 = resp.BodyContent;
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise2);
                try
                {
                    subject = global::TemperLang.Core.Async.GetPromiseResult(promise2);
                    body = subject == null ? "missing" : subject;
                    console.Log("body " + body);
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                goto OK1;
                CATCH1: ;
                {
                    console.Log("broken promise");
                }
                OK1: ;
            }
            {
                BrokenPromise e;
                Task<INetResponse> promise3;
                try
                {
                    promise3 = new NetRequest("http://127.0.0.1:1/").Send();
                }
                catch (BrokenPromise failure1)
                {
                    e = failure1;
                    goto CATCH2;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise3);
                try
                {
                    _ = global::TemperLang.Core.Async.GetPromiseResult(promise3);
                    console.Log("port 1 answered");
                }
                catch (BrokenPromise failure1)
                {
                    e = failure1;
                    goto CATCH2;
                }
                goto OK2;
                CATCH2: ;
                {
                    console.Log("no response from port 1: " + e.Message);
                }
                OK2: ;
            }
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
    }
}
