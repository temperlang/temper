package temper.std.regex;
import temper.core.Nullable;
import java.util.List;
import temper.core.Core;
import java.util.AbstractMap.SimpleImmutableEntry;
final class RegexRefs {
    public final CodePoints codePoints;
    public final Group group;
    public final Match match;
    public final Or orObject;
    public static final class Builder {
        @Nullable CodePoints codePoints;
        public Builder codePoints(@Nullable CodePoints codePoints) {
            this.codePoints = codePoints;
            return this;
        }
        @Nullable Group group;
        public Builder group(@Nullable Group group) {
            this.group = group;
            return this;
        }
        @Nullable Match match;
        public Builder match(@Nullable Match match) {
            this.match = match;
            return this;
        }
        @Nullable Or orObject;
        public Builder orObject(@Nullable Or orObject) {
            this.orObject = orObject;
            return this;
        }
        public RegexRefs build() {
            return new RegexRefs(codePoints, group, match, orObject);
        }
    }
    public RegexRefs(@Nullable CodePoints codePoints__539, @Nullable Group group__540, @Nullable Match match__541, @Nullable Or orObject__542) {
        CodePoints codePoints__251;
        if (codePoints__539 == null) {
            codePoints__251 = new CodePoints("");
        } else {
            codePoints__251 = codePoints__539;
        }
        Group group__252;
        if (group__540 == null) {
            group__252 = new Group("", "", 0, 0);
        } else {
            group__252 = group__540;
        }
        Match match__253;
        if (match__541 == null) {
            match__253 = new Match(group__252, Core.mapConstructor(List.of(new SimpleImmutableEntry<>("", group__252))));
        } else {
            match__253 = match__541;
        }
        Or orObject__254;
        if (orObject__542 == null) {
            orObject__254 = new Or(List.of());
        } else {
            orObject__254 = orObject__542;
        }
        this.codePoints = codePoints__251;
        this.group = group__252;
        this.match = match__253;
        this.orObject = orObject__254;
    }
    public RegexRefs(@Nullable CodePoints codePoints__539, @Nullable Group group__540, @Nullable Match match__541) {
        this(codePoints__539, group__540, match__541, null);
    }
    public RegexRefs(@Nullable CodePoints codePoints__539, @Nullable Group group__540) {
        this(codePoints__539, group__540, null, null);
    }
    public RegexRefs(@Nullable CodePoints codePoints__539) {
        this(codePoints__539, null, null, null);
    }
    public RegexRefs() {
        this(null, null, null, null);
    }
    public CodePoints getCodePoints() {
        return this.codePoints;
    }
    public Group getGroup() {
        return this.group;
    }
    public Match getMatch() {
        return this.match;
    }
    public Or getOrObject() {
        return this.orObject;
    }
}
