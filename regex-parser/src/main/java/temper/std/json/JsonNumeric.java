package temper.std.json;
public interface JsonNumeric extends JsonSyntaxTree {
    String asJsonNumericToken();
    int asInt32();
    long asInt64();
    double asFloat64();
}
