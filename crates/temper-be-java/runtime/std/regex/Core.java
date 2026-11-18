package temper.std.regex;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.unmodifiableMap;

/** Java native implementation for the temper.std.regex package. */
class Core {
    private Core() {
    }

    static String regexFormat(RegexNode data) {
        RegexFormatter f = new RegexFormatter();
        return f.format(data);
    }

    private static void groupNames(RegexNode regex, List<String> tgt) {
        if (regex instanceof Capture) {
            Capture cap = (Capture) regex;
            String name = cap.getName();
            tgt.add(name);
            groupNames(cap.getItem(), tgt);
        } else if (regex instanceof Or) {
            for (RegexNode child : ((Or) regex).getItems()) {
                groupNames(child, tgt);
            }
        } else if (regex instanceof Sequence) {
            for (RegexNode child : ((Sequence) regex).getItems()) {
                groupNames(child, tgt);
            }
        } else if (regex instanceof Repeat) {
            groupNames(((Repeat) regex).getItem(), tgt);
        }
    }

    private static class InternalPattern {
        /** The actual compiled pattern. */
        final Pattern pattern;
        /** The group names, as Pattern assumes we know what name we're searching for. */
        final List<String> namesPatternOrder;

        InternalPattern(Pattern pattern, List<String> names) {
            this.pattern = pattern;
            this.namesPatternOrder = names;
        }
    }

    static Object regexCompiledFormatted(RegexNode data, String text) {
        Pattern pattern = Pattern.compile(text);
        ArrayList<String> names = new ArrayList<>();
        groupNames(data, names);
        names.trimToSize();
        return new InternalPattern(pattern, names);
    }

    static boolean regexCompiledFound(Regex regex, Object compiled, String text) {
        return ((InternalPattern) compiled).pattern.matcher(text).find();
    }

    private static class ConvertGroups {
        final InternalPattern pat;
        final String text;

        ConvertGroups(InternalPattern pat, String text) {
            this.pat = pat;
            this.text = text;
        }

        Match convertGroups(Matcher matcher) {
            Map<String, Group> groups =
                new LinkedHashMap<>((pat.namesPatternOrder.size()) * 4 / 3 + 1, 0.75f);
            for (String name : pat.namesPatternOrder) {
                int begin = matcher.start(name);
                if (begin < 0) {
                    continue;
                }
                String value = matcher.group(name);
                groups.put(name, new Group(name, value, begin, begin + value.length()));
            }
            int begin = matcher.start();
            String value = matcher.group();
            Group full = new Group("full", value, begin, begin + value.length());
            return new Match(full, unmodifiableMap(groups));
        }
    }

    static Match regexCompiledFind(Regex regex, Object compiled, String text, int begin, RegexRefs refs) {
        InternalPattern internal = ((InternalPattern) compiled);
        Matcher matcher = internal.pattern.matcher(text);
        matcher.region(begin, text.length());
        if (!matcher.find()) {
            throw new RuntimeException();
        }
        return new ConvertGroups(internal, text).convertGroups(matcher);
    }

    static String regexCompiledReplace(
        Regex regex, Object compiled, String sourceText, Function<Match, String> replaceText, RegexRefs refs
    ) {
        InternalPattern internal = ((InternalPattern) compiled);
        Matcher matcher = internal.pattern.matcher(sourceText);
        StringBuilder sb = new StringBuilder();
        int prior = 0;
        ConvertGroups converter = new ConvertGroups(internal, sourceText);
        while (matcher.find()) {
            sb.append(sourceText, prior, matcher.start());
            prior = matcher.end();
            Match match = converter.convertGroups(matcher);
            String replacement = null;
            try {
                replacement = replaceText.apply(match);
            } catch (RuntimeException ignored) {
            }
            if (replacement != null) {
                sb.append(replacement);
            }
        }
        sb.append(sourceText, prior, sourceText.length());
        return sb.toString();
    }

    static List<String> regexCompiledSplit(Regex regex, Object compiled, String sourceText, RegexRefs refs) {
        InternalPattern internal = ((InternalPattern) compiled);
        return temper.core.Core.listOf(internal.pattern.split(sourceText));
    }

    static void regexFormatterPushCodeTo(RegexFormatter ignored, StringBuilder out, int code, boolean insideCodeSet) {
        String result;
        if (code < 32) {
            result = new Formatter().format("\\x%02x", code).toString();
        } else if (code < 127) {
            result = Character.toString((char) code);
        } else if (code < 256) {
            result = new Formatter().format("\\x%02x", code).toString();
        } else {
            result = new Formatter().format("\\u{%x}", code).toString();
        }
        out.append(result);
    }
}
