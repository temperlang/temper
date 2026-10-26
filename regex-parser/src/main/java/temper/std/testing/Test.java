package temper.std.testing;
import temper.core.Core;
import java.util.List;
import java.util.function.Supplier;
import temper.core.Nullable;
import java.util.function.Function;
import java.util.ArrayList;
public final class Test {
    public void assert_(boolean success__43, Supplier<String> message__44) {
        if (!success__43) {
            this._passing = false;
            Core.listAdd(this._messages, message__44.get());
        }
    }
    public void assertHard(boolean success__47, Supplier<String> message__48) {
        this.assert_(success__47, message__48);
        if (!success__47) {
            this._failedOnAssert = true;
            Core.throwAssertionError(this.messagesCombined());
        }
    }
    public void softFailToHard() {
        if (this.isHasUnhandledFail()) {
            this._failedOnAssert = true;
            Core.throwAssertionError(this.messagesCombined());
        }
    }
    public boolean isPassing() {
        return this._passing;
    }
    /**
     * Messages access is presented as a function because it likely allocates. Also,
     * messages might be automatically constructed in some cases, so it's possibly
     * unwise to depend on their exact formatting.
     */
    public List<String> messages() {
        return List.copyOf(this._messages);
    }
    public boolean isFailedOnAssert() {
        return this._failedOnAssert;
    }
    public boolean isHasUnhandledFail() {
        boolean t_151;
        if (this._failedOnAssert) {
            t_151 = true;
        } else {
            t_151 = this._passing;
        }
        return !t_151;
    }
    public @Nullable String messagesCombined() {
        if (this._messages.isEmpty()) {
            return null;
        } else {
            Function<String, String> fn__158 = it__64 -> it__64;
            return Core.listJoinObj(this._messages, ", ", fn__158);
        }
    }
    boolean _failedOnAssert;
    boolean _passing;
    final List<String> _messages;
    public Test() {
        this._failedOnAssert = false;
        this._passing = true;
        List<String> t_111 = new ArrayList<>();
        this._messages = t_111;
    }
}
