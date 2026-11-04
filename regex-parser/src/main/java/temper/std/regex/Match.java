package temper.std.regex;
import java.util.Map;
public final class Match {
    public final Group full;
    public final Map<String, Group> groups;
    public static final class Builder {
        Group full;
        public Builder full(Group full) {
            this.full = full;
            return this;
        }
        Map<String, Group> groups;
        public Builder groups(Map<String, Group> groups) {
            this.groups = groups;
            return this;
        }
        public Match build() {
            return new Match(full, groups);
        }
    }
    public Match(Group full__235, Map<String, Group> groups__236) {
        this.full = full__235;
        this.groups = groups__236;
    }
    public Group getFull() {
        return this.full;
    }
    public Map<String, Group> getGroups() {
        return this.groups;
    }
}
