package temper.regex_parser;
import temper.std.regex.RegexNode;
import temper.std.regex.Regex;
import temper.core.Core;
import java.util.Map;
public final class RegexParserGlobal {
    private RegexParserGlobal() {
    }
    public static RegexNode parseWith(String src__171, Map<String, RegexNode> slots__172) {
        Parser parser__174 = new Parser(src__171, slots__172);
        RegexNode result__175 = parser__174.readOr();
        if (!parser__174.isDone()) {
            parser__174.error("unexpected ')'; unmatched closing parenthesis");
            throw Core.bubble();
        }
        return result__175;
    }
    public static Regex compileWith(String src__166, Map<String, RegexNode> slots__167) {
        RegexNode t_395 = RegexParserGlobal.parseWith(src__166, slots__167);
        return t_395.compiled();
    }
    public static RegexNode parse(String src__176) {
        Parser parser__178 = new Parser(src__176);
        RegexNode result__179 = parser__178.readOr();
        if (!parser__178.isDone()) {
            parser__178.error("unexpected ')'; unmatched closing parenthesis");
            throw Core.bubble();
        }
        return result__179;
    }
    public static Regex compile(String src__169) {
        RegexNode t_394 = RegexParserGlobal.parse(src__169);
        return t_394.compiled();
    }
}
