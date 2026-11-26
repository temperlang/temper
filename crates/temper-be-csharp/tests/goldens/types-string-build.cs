using System.Text;
using TemperLang.Core;

namespace Build;

public static class BuildGlobal
{
    static BuildGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Build");
        StringBuilder builder = new StringBuilder();
        builder.Append("Hi ");
        StringUtil.AppendCodePoint(builder, 127759);
        string source = "Wow!! Neato!";
        int bangIndex = StringUtil.Step(source, 0, 3);
        int spaceIndex = StringUtil.Step(source, bangIndex, 2);
        StringUtil.AppendBetween(builder, source, bangIndex, spaceIndex);
        string built = builder.ToString();
        builder.Append('\n');
        builder.Append(built);
        console.Log(builder.ToString());
        try
        {
            StringUtil.AppendCodePoint(builder, 1114112);
        }
        catch (InvalidCodePoint)
        {
            console.Log("High code blocked.");
        }
        try
        {
            StringUtil.AppendCodePoint(builder, 55296);
        }
        catch (InvalidCodePoint e)
        {
            console.Log("Surrogate 0x" + Core.ConvertToString(e.CodePoint, 16) + " blocked.");
        }
        console.Log(builder.ToString());
        builder.Clear();
        builder.Append("Back to square zero.");
        console.Log(builder.ToString());
        builder.Clear();
        builder.Append("a??=b??/c??>d???e");
        string marks = builder.ToString();
        console.Log(marks);
        console.Log("Same: " + (marks == "a??=b??/c??>d???e" ? "true" : "false"));
        builder.Clear();
        builder.Append("??");
        try
        {
            StringUtil.AppendCodePoint(builder, 1);
        }
        catch (InvalidCodePoint)
        {
            console.Log("No U+1.");
        }
        builder.Append('0');
        try
        {
            StringUtil.AppendCodePoint(builder, 8364);
        }
        catch (InvalidCodePoint)
        {
            console.Log("No euro.");
        }
        builder.Append('1');
        console.Log("Digits after escapes: " + (builder.ToString() == "??\u00010€1" ? "true" : "false"));
        StringBuilderOwner owner = new StringBuilderOwner(new StringBuilder());
        owner.Builder.Append("Hi from inside.");
        console.Log(owner.Builder.ToString());
    }
}
