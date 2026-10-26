package temper.std.json;
import temper.core.Core;
import java.util.List;
import temper.core.Nullable;
import java.util.ArrayList;
import static temper.std.json.NullInterchangeContext.instance;
public final class JsonTextProducer implements JsonProducer {
    public final InterchangeContext interchangeContext;
    final StringBuilder buffer;
    final List<Integer> stack;
    boolean wellFormed;
    public JsonTextProducer(@Nullable InterchangeContext interchangeContext__963) {
        InterchangeContext interchangeContext__552;
        if (interchangeContext__963 == null) {
            interchangeContext__552 = instance;
        } else {
            interchangeContext__552 = interchangeContext__963;
        }
        this.interchangeContext = interchangeContext__552;
        StringBuilder t_889 = new StringBuilder();
        this.buffer = t_889;
        List<Integer> t_888 = new ArrayList<>();
        this.stack = t_888;
        Core.listAdd(this.stack, 5);
        this.wellFormed = true;
    }
    public JsonTextProducer() {
        this(null);
    }
    int state() {
        return Core.listGetOr(this.stack, this.stack.size() - 1, -1);
    }
    void beforeValue() {
        int currentState__558 = this.state();
        switch (currentState__558) {
            case 3:
                {
                this.stack.set(this.stack.size() - 1, 4);
                break;
            }
            case 4:
                {
                this.buffer.append(",");
                break;
            }
            case 1:
                {
                this.stack.set(this.stack.size() - 1, 2);
                break;
            }
            case 5:
                {
                this.stack.set(this.stack.size() - 1, 6);
                break;
            }
            default:
                {
                boolean t_1135;
                if (currentState__558 == 6) {
                    t_1135 = true;
                } else {
                    t_1135 = currentState__558 == 2;
                }
                if (t_1135) {
                    this.wellFormed = false;
                }
                break;
            }
        }
    }
    public void startObject() {
        this.beforeValue();
        this.buffer.append("{");
        Core.listAdd(this.stack, 0);
    }
    public void endObject() {
        this.buffer.append("}");
        int currentState__563 = this.state();
        boolean t_1133;
        if (0 == currentState__563) {
            t_1133 = true;
        } else {
            t_1133 = 2 == currentState__563;
        }
        if (t_1133) {
            Core.listRemoveLast(this.stack);
        } else {
            this.wellFormed = false;
        }
    }
    public void objectKey(String key__565) {
        int currentState__567 = this.state();
        if (currentState__567 != 0) {
            if (currentState__567 == 2) {
                this.buffer.append(",");
            } else {
                this.wellFormed = false;
            }
        }
        JsonGlobal.encodeJsonString(key__565, this.buffer);
        this.buffer.append(":");
        if (currentState__567 >= 0) {
            this.stack.set(this.stack.size() - 1, 1);
        }
    }
    public void startArray() {
        this.beforeValue();
        this.buffer.append("[");
        Core.listAdd(this.stack, 3);
    }
    public void endArray() {
        this.buffer.append("]");
        int currentState__572 = this.state();
        boolean t_1131;
        if (3 == currentState__572) {
            t_1131 = true;
        } else {
            t_1131 = 4 == currentState__572;
        }
        if (t_1131) {
            Core.listRemoveLast(this.stack);
        } else {
            this.wellFormed = false;
        }
    }
    public void nullValue() {
        this.beforeValue();
        this.buffer.append("null");
    }
    public void booleanValue(boolean x__576) {
        String t_1129;
        this.beforeValue();
        StringBuilder t_1130 = this.buffer;
        if (x__576) {
            t_1129 = "true";
        } else {
            t_1129 = "false";
        }
        t_1130.append(t_1129);
    }
    public void int32Value(int x__579) {
        this.beforeValue();
        this.buffer.append(Integer.toString(x__579));
    }
    public void int64Value(long x__582) {
        this.beforeValue();
        this.buffer.append(Long.toString(x__582));
    }
    public void float64Value(double x__585) {
        this.beforeValue();
        this.buffer.append(Core.float64ToString(x__585));
    }
    public void numericTokenValue(String x__588) {
        this.beforeValue();
        this.buffer.append(x__588);
    }
    public void stringValue(String x__591) {
        this.beforeValue();
        JsonGlobal.encodeJsonString(x__591, this.buffer);
    }
    public String toJsonString() {
        boolean t_1126;
        if (this.wellFormed) {
            if (this.stack.size() == 1) {
                t_1126 = this.state() == 6;
            } else {
                t_1126 = false;
            }
        } else {
            t_1126 = false;
        }
        if (t_1126) {
            return this.buffer.toString();
        } else {
            throw Core.bubble();
        }
    }
    public InterchangeContext getInterchangeContext() {
        return this.interchangeContext;
    }
}
