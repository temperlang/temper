using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;
using TemperLang.Std.Net;

namespace Netresponse;

public static class NetresponseGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly string url;

    static NetresponseGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Netresponse");
        url = "http://127.0.0.1:" + 28461.ToString(CultureInfo.InvariantCulture);
        IEnumerable<Tuple<object?>> fnSteps1()
        {
            {
                NetRequest req;
                Task<INetResponse> promise1;
                INetResponse resp;
                string body;
                Task<string?> promise2;
                string? subject;
                try
                {
                    req = new NetRequest(url);
                    req.Post("[]", "application/json");
                    promise1 = req.Send();
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                try
                {
                    resp = global::TemperLang.Core.Async.GetPromiseResult(promise1);
                    if (!(resp.Status == 200))
                    {
                        goto ELSE1;
                    }
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
                    console.Log("Thanks, server.  I got " + body + ".");
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                goto END1;
                ELSE1: ;
                try
                {
                    console.Log("HTTP status was not 200: " + resp.Status.ToString(CultureInfo.InvariantCulture) + "!");
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                END1: ;
                goto OK1;
                CATCH1: ;
                {
                    console.Log("failed");
                }
                OK1: ;
            }
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
    }
}
