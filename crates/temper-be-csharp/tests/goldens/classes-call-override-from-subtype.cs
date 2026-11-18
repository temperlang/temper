using System.Globalization;
using TemperLang.Core;

namespace CallOverrideFromSubtype;

public static class CallOverrideFromSubtypeGlobal
{
    static CallOverrideFromSubtypeGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("CallOverrideFromSubtype");
        Slippy slippy = new Slippy();
        ISlappy slappy = slippy;
        console.Log(slippy.Thingums.ToString(CultureInfo.InvariantCulture));
        console.Log(slappy.Thingums.ToString(CultureInfo.InvariantCulture));
        console.Log(slippy.Thingumses.ToString(CultureInfo.InvariantCulture));
        console.Log(slippy.CheckOverridden() ? "true" : "false");
        console.Log(slippy.CheckOwn() ? "true" : "false");
        console.Log(((IC)new D()).A());
        console.Log(((IC)new D()).Thing);
        console.Log(((ICSplit)new DSplit()).A());
        console.Log(((IB1_Deep)new DDeep()).A());
        console.Log(((IADeep)new EDeep()).A());
    }
}
