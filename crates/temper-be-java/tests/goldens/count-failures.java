package count_failures;

public final class CountFailuresGlobal {
    private CountFailuresGlobal() {}

    public static int parseCount(String text) throws BadCount {
        if (text.equals("zero")) {
            return 0;
        } else if (text.equals("two")) {
            return 2;
        } else {
            throw new BadCount(text);
        }
    }
}
