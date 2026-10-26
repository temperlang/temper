package temper.regex_parser;
import temper.core.Core;
public final class RegexParserMain {
    private RegexParserMain() {
    }
    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Class.forName("temper.regex_parser.RegexParserGlobal");
        Core.waitUntilTasksComplete();
    }
}
