package zero_advance;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.std.regex.CodePoints;
import temper.std.regex.Or;
import temper.std.regex.Regex;
import temper.std.regex.RegexGlobal;
import temper.std.regex.RegexNode;
import temper.std.regex.Repeat;
import temper.std.regex.Sequence;

public final class ZeroAdvanceGlobal {
    private ZeroAdvanceGlobal() {}

    static final Regex commaItemStartRegex;

    public static String bulletizeList(String text) {
        String replaced = commaItemStartRegex.replace(text, match -> "\n- ");
        return Core.stringSlice(replaced, Core.stringNext(replaced, 0), replaced.length());
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("zero_advance"));
        commaItemStartRegex = new Sequence(List.<RegexNode>of(new Or(List.<RegexNode>of(RegexGlobal.Begin, new CodePoints(","))), new Repeat(RegexGlobal.Space, 0, null, false))).compiled();
        console.log(bulletizeList("apples, bananas, cherries"));
    }
}
