using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace Map;

public static class MapGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly IReadOnlyDictionary<int, string> messages;
    internal static readonly IDictionary<string, IReadOnlyList<bool>> eightColors;

    internal static IReadOnlyList<K> ListKeys<K, V>(IReadOnlyDictionary<K, V> mapped)
    {
        return Listed.ToReadOnlyList(Enumerable.Select(Mapped.ToList(mapped), (KeyValuePair<K, V> entry) => entry.Key));
    }

    internal static string MessageDefined(int code)
    {
        return messages.ContainsKey(code) ? "true" : "false";
    }

    internal static string ColorToHex(IReadOnlyList<bool> color)
    {
        string fn(bool b)
        {
            if (b)
            {
                return "FF";
            }
            else
            {
                return "00";
            }
        }
        string c = Listed.Join(color, "", (Func<bool, string>)fn);
        return "#" + c;
    }

    internal static void PrintMap(string name, IReadOnlyDictionary<string, string> map)
    {
        console.Log("--- " + name + " ---");
        Mapped.ForEach(map, (string key, string value) => console.Log(key + " = " + value));
    }

    internal static void PrintMapBuilder(string name, IDictionary<string, string> map)
    {
        console.Log("--- " + name + " ---");
        Mapped.ForEach(map, (string key, string value) => console.Log(key + " = " + value));
    }

    static MapGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Map");
        messages = Mapped.ConstructMap(Listed.CreateReadOnlyList<KeyValuePair<int, string>>(new KeyValuePair<int, string>(200, "OK"), new KeyValuePair<int, string>(404, "Not Found"), new KeyValuePair<int, string>(500, "Internal Server Error")));
        string message = Mapped.Get(messages, 200);
        console.Log("Message: " + message);
        console.Log("Has 200: " + MessageDefined(200) + ", 302: " + MessageDefined(302));
        string t1;
        try
        {
            t1 = Mapped.Get(messages, 302);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t1 = "Found";
        }
        console.Log("Fallback: " + t1);
        console.Log("Codes: " + Listed.Join(ListKeys(messages), " ", (int it) => it.ToString(CultureInfo.InvariantCulture)));
        console.Log("Again: " + Listed.Join(Mapped.Keys(messages), " ", (int it) => it.ToString(CultureInfo.InvariantCulture)));
        console.Log("Messages: " + Listed.Join(Mapped.Values(messages), ", ", (string it) => it));
        IReadOnlyList<int> groups = Mapped.ToListWith(messages, (int key, string value) => Core.Div(key, 100));
        console.Log("Groups: " + Listed.Join(groups, " ", (int it) => it.ToString(CultureInfo.InvariantCulture)));
        IReadOnlyDictionary<string, IReadOnlyList<string>> neighbors;
        IDictionary<string, IReadOnlyList<string>> builder2 = new TemperLang.Core.OrderedDictionary<string, IReadOnlyList<string>>();
        builder2["Honduras"] = Listed.CreateReadOnlyList<string>("El Salvador", "Guatemala", "Nicaragua");
        console.Log("From builder: " + Listed.Join(Mapped.Get(builder2, "Honduras"), ", ", (string it) => it));
        console.Log("Key so far: " + Listed.Get(ListKeys(Mapped.AsReadOnly(builder2)), 0));
        builder2["El Salvador"] = Listed.CreateReadOnlyList<string>("Guatemala", "Honduras");
        builder2["Nicaragua"] = Listed.CreateReadOnlyList<string>("Costa Rica", "Honduras");
        builder2["Panama"] = Listed.CreateReadOnlyList<string>("Colombia", "Costa Rica");
        try
        {
            Core.RemoveGet(builder2, "Honduras");
        }
        catch (global::TemperLang.Core.Bubble)
        {
        }
        IReadOnlyList<string> was = Core.RemoveGet(builder2, "El Salvador");
        console.Log("Was there: " + Listed.Join(was, ", ", (string it) => it));
        string t2;
        try
        {
            t2 = Listed.Get(Core.RemoveGet(builder2, "El Salvador"), 0);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t2 = "Gone";
        }
        console.Log(t2);
        neighbors = Mapped.ToMap(builder2);
        console.Log("From built: " + Listed.Join(Mapped.Get(neighbors, "Nicaragua"), ", ", (string it) => it));
        IReadOnlyDictionary<int, int> mapOfNone = Mapped.ConstructMap(Listed.CreateReadOnlyList<KeyValuePair<int, int>>());
        IReadOnlyDictionary<string, string> mapOfOne = Mapped.ConstructMap(Listed.CreateReadOnlyList<KeyValuePair<string, string>>(new KeyValuePair<string, string>("jack", "all trades")));
        IReadOnlyDictionary<int, string> mapOfTwo = Mapped.ConstructMap(Listed.CreateReadOnlyList<KeyValuePair<int, string>>(new KeyValuePair<int, string>(1, "one"), new KeyValuePair<int, string>(2, "electric boogaloo")));
        console.Log("mapOfNone: " + mapOfNone.Count.ToString(CultureInfo.InvariantCulture));
        console.Log("mapOfOne: " + mapOfOne.Count.ToString(CultureInfo.InvariantCulture));
        console.Log("mapOfTwo: " + mapOfTwo.Count.ToString(CultureInfo.InvariantCulture));
        eightColors = new TemperLang.Core.OrderedDictionary<string, IReadOnlyList<bool>>();
        eightColors["black"] = Listed.CreateReadOnlyList<bool>(false, false, false);
        eightColors["red"] = Listed.CreateReadOnlyList<bool>(true, false, false);
        eightColors["green"] = Listed.CreateReadOnlyList<bool>(false, true, false);
        eightColors["blue"] = Listed.CreateReadOnlyList<bool>(false, false, true);
        eightColors["yellow"] = Listed.CreateReadOnlyList<bool>(true, true, false);
        eightColors["cyan"] = Listed.CreateReadOnlyList<bool>(false, true, true);
        eightColors["purple"] = Listed.CreateReadOnlyList<bool>(true, false, true);
        eightColors["white"] = Listed.CreateReadOnlyList<bool>(true, true, true);
        console.Log("with 3 bits you can draw " + eightColors.Count.ToString(CultureInfo.InvariantCulture) + " colors.");
        void fn9(string color, IReadOnlyList<bool> bits)
        {
            IReadOnlyList<bool> foundByGetOr = Mapped.GetOrDefault(eightColors, color, Listed.CreateReadOnlyList<bool>());
            if (foundByGetOr.Count != 3)
            {
                console.Log("getOr(\"" + color + "\") failed to find key");
            }
            string want = ColorToHex(bits);
            string got = ColorToHex(foundByGetOr);
            if (want != got)
            {
                console.Log("getOr(\"" + color + "\") found " + got + " but wanted " + want);
            }
        }
        Mapped.ForEach(eightColors, (Action<string, IReadOnlyList<bool>>)fn9);
        IReadOnlyDictionary<string, string> map = Mapped.ConstructMap(Listed.CreateReadOnlyList<KeyValuePair<string, string>>(new KeyValuePair<string, string>("key", "value"), new KeyValuePair<string, string>("other key", "other value")));
        PrintMap("map", map);
        IDictionary<string, string> builder = Mapped.ToMapBuilder(map);
        builder["added key"] = "added value";
        builder["key"] = "replaced value";
        PrintMapBuilder("builder", builder);
        PrintMap("map (same as above)", map);
        IReadOnlyDictionary<string, string> newMap = Mapped.ToMap(builder);
        PrintMap("builder to map", newMap);
        IReadOnlyDictionary<string, string> builderAsMapped = Mapped.AsReadOnly(builder);
        IReadOnlyDictionary<string, string> newMapFromMapped = Mapped.ToMap(builderAsMapped);
        Core.RemoveGet(builder, "other key");
        builder["key"] = "double replaced value";
        PrintMap("builder to map (same as above)", newMap);
        PrintMap("builder to map via mapped (same as above)", newMapFromMapped);
        builder.Clear();
        builder["something"] = "alone";
        PrintMapBuilder("recently cleared", builder);
        IReadOnlyDictionary<string, string> mayOrMayNotCopyMap = Mapped.ToMap(map);
        IReadOnlyDictionary<string, string> mapped = map;
        IReadOnlyDictionary<string, string> mayOrMayNotCopyMapped = Mapped.ToMap(mapped);
        IDictionary<string, string> madeBy = new TemperLang.Core.OrderedDictionary<string, string>();
        madeBy["temper"] = "temper systems";
        PrintMapBuilder("made by", madeBy);
        IDictionary<string, string> madeByAndJokes = Mapped.ToMapBuilder(madeBy);
        madeByAndJokes["milk"] = "milking a cow";
        PrintMapBuilder("made by (and a joke about milk)", madeByAndJokes);
        PrintMapBuilder("made by (not changed from a change to a copy)", madeBy);
    }
}
