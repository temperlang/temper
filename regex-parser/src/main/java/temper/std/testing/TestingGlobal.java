package temper.std.testing;
import java.util.List;
import java.util.function.Consumer;
import temper.core.Core;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.ArrayList;
import temper.std.testing.function.Int32PairInt32;
import java.util.AbstractMap.SimpleImmutableEntry;
public final class TestingGlobal {
    private TestingGlobal() {
    }
    public static List<Entry<String, List<String>>> processTestCases(List<Entry<String, Consumer<Test>>> testCases__69) {
        Function<Entry<String, Consumer<Test>>, Entry<String, List<String>>> fn__157 = testCase__71 -> {
            String key__73 = testCase__71.getKey();
            Consumer<Test> fun__74 = testCase__71.getValue();
            Test test__75 = new Test();
            boolean hadBubble__76 = false;
            try {
                fun__74.accept(test__75);
            } catch (RuntimeException ignored$1) {
                hadBubble__76 = true;
            }
            List<String> messages__77 = test__75.messages();
            List<String> failures__78;
            boolean t_145;
            if (test__75.isPassing()) {
                t_145 = !hadBubble__76;
            } else {
                t_145 = false;
            }
            if (t_145) {
                failures__78 = List.of();
            } else {
                boolean t_148;
                if (hadBubble__76) {
                    t_148 = !test__75.isFailedOnAssert();
                } else {
                    t_148 = false;
                }
                if (t_148) {
                    List<String> allMessages__79 = new ArrayList<>(messages__77);
                    Core.listAdd(allMessages__79, "Bubble");
                    failures__78 = List.copyOf(allMessages__79);
                } else {
                    failures__78 = messages__77;
                }
            }
            return new SimpleImmutableEntry<>(key__73, failures__78);
        };
        return Core.listMapObjToObj(testCases__69, fn__157);
    }
    /**
     * escapeXml takes a string and escapes it so that it has the same meaning as an
     * XML text node or attribute value.
     */
    static String escapeXml(String s__103) {
        StringBuilder sb__105 = new StringBuilder();
        int end__106 = s__103.length();
        int emitted__107 = 0;
        int i__108 = 0;
        while (i__108 < end__106) {
            continue_159: {
                int c__109 = s__103.codePointAt(i__108);
                String esc__110;
                switch (c__109) {
                    case 38:
                        {
                        esc__110 = "&amp;";
                        break;
                    }
                    case 60:
                        {
                        esc__110 = "&lt;";
                        break;
                    }
                    case 62:
                        {
                        esc__110 = "&gt;";
                        break;
                    }
                    case 39:
                        {
                        esc__110 = "&#39;";
                        break;
                    }
                    case 34:
                        {
                        esc__110 = "&#34;";
                        break;
                    }
                    default:
                        {
                        boolean t_132;
                        switch (c__109) {
                            case 10:
                                {
                                t_132 = true;
                                break;
                            }
                            case 13:
                                {
                                t_132 = true;
                                break;
                            }
                            default:
                                {
                                t_132 = c__109 == 9;
                                break;
                            }
                        }
                        if (t_132) {
                            break continue_159;
                        } else {
                            boolean t_136;
                            if (c__109 < 32) {
                                t_136 = true;
                            } else if (c__109 == 65534) {
                                t_136 = true;
                            } else {
                                t_136 = c__109 == 65535;
                            }
                            if (t_136) {
                                esc__110 = "[0x" + Integer.toString(c__109, 16) + "]";
                            } else {
                                break continue_159;
                            }
                        }
                        break;
                    }
                }
                Core.stringBuilderAppendBetween(sb__105, s__103, emitted__107, i__108);
                sb__105.append(esc__110);
                emitted__107 = Core.stringNext(s__103, i__108);
            }
            i__108 = Core.stringNext(s__103, i__108);
        }
        if (emitted__107 == 0) {
            return s__103;
        } else {
            Core.stringBuilderAppendBetween(sb__105, s__103, emitted__107, end__106);
            return sb__105.toString();
        }
    }
    public static void reportTestResults(List<Entry<String, List<String>>> testResults__80, Consumer<String> writeLine__81) {
        writeLine__81.accept("<testsuites>");
        String total__83 = Integer.toString(testResults__80.size());
        Int32PairInt32 fn__156 = (fails__85, testResult__86) -> {
            int t_131;
            if (testResult__86.getValue().isEmpty()) {
                t_131 = 0;
            } else {
                t_131 = 1;
            }
            return fails__85 + t_131;
        };
        String fails__84 = Integer.toString(Core.listedReduceObjToInt(testResults__80, 0, fn__156 :: applyAsInt));
        String totals__88 = "tests='" + total__83 + "' failures='" + fails__84 + "'";
        writeLine__81.accept("  <testsuite name='suite' " + totals__88 + " time='0.0'>");
        int i__89 = 0;
        while (i__89 < testResults__80.size()) {
            Entry<String, List<String>> testResult__90 = Core.listGet(testResults__80, i__89);
            List<String> failureMessages__91 = testResult__90.getValue();
            String name__92 = TestingGlobal.escapeXml(testResult__90.getKey());
            String basics__93 = "name='" + name__92 + "' classname='" + name__92 + "' time='0.0'";
            if (failureMessages__91.isEmpty()) {
                writeLine__81.accept("    <testcase " + basics__93 + " />");
            } else {
                writeLine__81.accept("    <testcase " + basics__93 + ">");
                Function<String, String> fn__155 = it__95 -> it__95;
                String message__94 = TestingGlobal.escapeXml(Core.listJoinObj(failureMessages__91, ", ", fn__155));
                writeLine__81.accept("      <failure message='" + message__94 + "' />");
                writeLine__81.accept("    </testcase>");
            }
            i__89 = i__89 + 1;
        }
        writeLine__81.accept("  </testsuite>");
        writeLine__81.accept("</testsuites>");
    }
    public static String runTestCases(List<Entry<String, Consumer<Test>>> testCases__96) {
        StringBuilder report__98 = new StringBuilder();
        Consumer<String> fn__154 = line__99 -> {
            report__98.append(line__99);
            report__98.append("\n");
        };
        TestingGlobal.reportTestResults(TestingGlobal.processTestCases(testCases__96), fn__154);
        return report__98.toString();
    }
    public static void runTest(Consumer<Test> testFun__100) {
        Test test__102 = new Test();
        try {
            testFun__100.accept(test__102);
        } catch (RuntimeException ignored$2) {
            Supplier<String> fn__153 = () -> "bubble during test running";
            test__102.assert_(false, fn__153);
        }
        test__102.softFailToHard();
    }
}
