package content_tags;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class ContentTagsGlobal {
    private ContentTagsGlobal() {}

    static final Console console;

    static void ignore(String problem, boolean isError) {
        if (isError) {
            console.log(problem);
        }
    }

    public static After propagateOver(Propagator p, boolean before, @Nullable String known, BiPredicate<Boolean, Boolean> same, BiConsumer<String, Boolean> callout) {
        if (known == null) {
            return new After(List.of(), before);
        }
        if (known.equals("!")) {
            return new After(List.of(new EventParseEffect(new Mark("bang"))), before);
        }
        int angles = Core.stringSplit(known, "<").size();
        if (angles > 1) {
            callout.accept("angle bracket in fixed text", false);
        }
        if (!same.test(before, before)) {
            callout.accept("states differ from themselves", true);
        }
        int quotes = Core.stringSplit(known, p.quote).size() - 1;
        return new After(List.of(new AppendParseEffect(known)), quotes % 2 == 1 ? !before : before);
    }

    static String greet(String name, String who) {
        {
            Buffer collector = Quoting.newCollector();
            collector.append("say 'hi ");
            collector.append(QuoteEscaper.getInstance().applyString(TextEscaper.getInstance().applyString(name)));
            collector.append("' to ");
            collector.append(TextEscaper.getInstance().applyString(who));
            Quoting.enact(new Mark("bang"), collector);
            return Quoting.fromCollector(collector);
        }
    }

    static String branch(boolean c, String x) {
        {
            Buffer collector = Quoting.newCollector();
            collector.append(TextEscaper.getInstance().applyString(x));
            if (c) {
                collector.append("''");
            }
            collector.append(" '");
            collector.append(QuoteEscaper.getInstance().applyString(TextEscaper.getInstance().applyString(x)));
            collector.append("'");
            return Quoting.fromCollector(collector);
        }
    }

    static String each(List<String> xs) {
        {
            Buffer collector = Quoting.newCollector();
            {
                int length = xs.size();
                int i = 0;
                while (i < length) {
                    String x = Core.listGet(xs, i);
                    i = i + 1;
                    collector.append("'");
                    collector.append(QuoteEscaper.getInstance().applyString(TextEscaper.getInstance().applyString(x)));
                    collector.append("' ");
                    collector.append(TextEscaper.getInstance().applyString(x));
                    collector.append(";");
                }
            }
            return Quoting.fromCollector(collector);
        }
    }

    static String counted(int n, String x) {
        {
            Buffer collector = Quoting.newCollector();
            {
                int i = 0;
                while (i < n) {
                    i = i + 1;
                    if (i == 2) {
                        continue;
                    }
                    collector.append("'");
                    collector.append(QuoteEscaper.getInstance().applyString(TextEscaper.getInstance().applyString(x)));
                    collector.append("'");
                    if (i == 4) {
                        break;
                    }
                    collector.append(TextEscaper.getInstance().applyString(x));
                    collector.append(",");
                }
            }
            return Quoting.fromCollector(collector);
        }
    }

    static String attribute(boolean c, String x) {
        {
            Buffer collector = Quoting.newCollector();
            collector.append("a='");
            if (c) {
                return "none";
            }
            collector.append(QuoteEscaper.getInstance().applyString(TextEscaper.getInstance().applyString(x)));
            collector.append("'");
            return Quoting.fromCollector(collector);
        }
    }

    static String opened(List<String> xs) {
        {
            Quoting accumulator = new Quoting();
            {
                int length = xs.size();
                int i = 0;
                while (i < length) {
                    String x = Core.listGet(xs, i);
                    i = i + 1;
                    accumulator.appendSafe("'");
                    accumulator.append(x);
                }
            }
            return accumulator.getAccumulated();
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("content_tags"));
        console.log(greet("Ann", "Bob"));
        console.log(branch(true, "a"));
        console.log(branch(false, "b"));
        console.log(each(List.of("c", "d")));
        console.log(counted(5, "e"));
        console.log(attribute(false, "f") + " " + attribute(true, "f"));
        console.log(opened(List.of("g", "h", "i")));
    }
}
