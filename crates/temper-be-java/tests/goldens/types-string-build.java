package build;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.InvalidCodePoint;

public final class BuildGlobal {
    private BuildGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("build"));
        StringBuilder builder = new StringBuilder();
        builder.append("Hi ");
        Core.stringBuilderAppendCodePoint(builder, 127759);
        String source = "Wow!! Neato!";
        int bangIndex = Core.stringStep(source, 0, 3);
        int spaceIndex = Core.stringStep(source, bangIndex, 2);
        Core.stringBuilderAppendBetween(builder, source, bangIndex, spaceIndex);
        String built = builder.toString();
        builder.append("\n");
        builder.append(built);
        console.log(builder.toString());
        try {
            Core.stringBuilderAppendCodePoint(builder, 1114112);
        } catch (InvalidCodePoint ignored) {
            console.log("High code blocked.");
        }
        try {
            Core.stringBuilderAppendCodePoint(builder, 55296);
        } catch (InvalidCodePoint e) {
            console.log("Surrogate 0x" + Integer.toString(e.codePoint, 16) + " blocked.");
        }
        console.log(builder.toString());
        builder.setLength(0);
        builder.append("Back to square zero.");
        console.log(builder.toString());
        builder.setLength(0);
        builder.append("a??=b??/c??>d???e");
        String marks = builder.toString();
        console.log(marks);
        console.log("Same: " + marks.equals("a??=b??/c??>d???e"));
        builder.setLength(0);
        builder.append("??");
        try {
            Core.stringBuilderAppendCodePoint(builder, 1);
        } catch (InvalidCodePoint ignored2) {
            console.log("No U+1.");
        }
        builder.append("0");
        try {
            Core.stringBuilderAppendCodePoint(builder, 8364);
        } catch (InvalidCodePoint ignored3) {
            console.log("No euro.");
        }
        builder.append("1");
        console.log("Digits after escapes: " + builder.toString().equals("??\u00010€1"));
        StringBuilderOwner owner = new StringBuilderOwner(new StringBuilder());
        owner.builder.append("Hi from inside.");
        console.log(owner.builder.toString());
    }
}
