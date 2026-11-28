package count_errors;

public final class CountErrorsGlobal {
    private CountErrorsGlobal() {}

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
