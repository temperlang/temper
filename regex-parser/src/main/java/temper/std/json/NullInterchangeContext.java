package temper.std.json;
import temper.core.Nullable;
public final class NullInterchangeContext implements InterchangeContext {
    public @Nullable String getHeader(String headerName__392) {
        return null;
    }
    public static final NullInterchangeContext instance = new temper.std.json.NullInterchangeContext();
    public NullInterchangeContext() {
    }
}
