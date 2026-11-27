package map;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.MissingKey;
import temper.core.Nullable;

public final class MapGlobal {
    private MapGlobal() {}

    static final Console console;
    static final Map<Integer, String> messages;
    static final Map<String, List<Boolean>> eightColors;

    static <K, V> List<K> listKeys(Map<K, V> mapped) {
        return Core.listMapObjToObj(Core.mappedToList(mapped), entry -> entry.getKey());
    }

    static String messageDefined(int code) {
        return Boolean.toString(messages.containsKey(code));
    }

    static String lookUp(int code) throws MissingKey {
        return Core.mappedGet(messages, code);
    }

    static String colorToHex(List<Boolean> color) {
        Function<Boolean, String> fn = b -> {
            if (b) {
                return "FF";
            } else {
                return "00";
            }
        };
        String c = Core.listJoinBool(color, "", fn);
        return "#" + c;
    }

    static void printMap(String name, Map<String, String> map) {
        console.log("--- " + name + " ---");
        Core.mappedForEach(map, (key, value) -> console.log(key + " = " + value));
    }

    static void printMapBuilder(String name, Map<String, String> map) {
        console.log("--- " + name + " ---");
        Core.mappedForEach(map, (key, value) -> console.log(key + " = " + value));
    }

    static {
        console = Core.getConsole(Logger.getLogger("map"));
        messages = Core.mapConstructor(List.of(new SimpleImmutableEntry<>(200, "OK"), new SimpleImmutableEntry<>(404, "Not Found"), new SimpleImmutableEntry<>(500, "Internal Server Error")));
        String message = Core.mappedGet(messages, 200);
        console.log("Message: " + message);
        console.log("Has 200: " + messageDefined(200) + ", 302: " + messageDefined(302));
        String t;
        try {
            t = Core.mappedGet(messages, 302);
        } catch (MissingKey ignored) {
            t = "Found";
        }
        console.log("Fallback: " + t);
        @Nullable String subject = messages.getOrDefault(302, null);
        console.log("Or null: " + (subject == null ? "none" : subject));
        console.log("Codes: " + Core.listJoinInt(listKeys(messages), " ", it -> Integer.toString(it)));
        console.log("Again: " + Core.listJoinInt(List.copyOf(messages.keySet()), " ", it -> Integer.toString(it)));
        console.log("Messages: " + Core.listJoinObj(Core.listCopyOf(messages.values()), ", ", it -> it));
        List<Integer> groups = Core.mappedToListWith(messages, (key, value) -> key / 100);
        console.log("Groups: " + Core.listJoinInt(groups, " ", it -> Integer.toString(it)));
        Map<String, List<String>> builder2 = new LinkedHashMap<>();
        builder2.put("Honduras", List.of("El Salvador", "Guatemala", "Nicaragua"));
        console.log("From builder: " + Core.listJoinObj(Core.mappedGet(builder2, "Honduras"), ", ", it -> it));
        console.log("Key so far: " + Core.listGet(listKeys(builder2), 0));
        builder2.put("El Salvador", List.of("Guatemala", "Honduras"));
        builder2.put("Nicaragua", List.of("Costa Rica", "Honduras"));
        builder2.put("Panama", List.of("Colombia", "Costa Rica"));
        try {
            Core.mapBuilderRemove(builder2, "Honduras");
        } catch (MissingKey ignored2) {
        }
        List<String> was;
        try {
            was = Core.mapBuilderRemove(builder2, "El Salvador");
        } catch (MissingKey failure) {
            throw new AssertionError("panic", failure);
        }
        console.log("Was there: " + Core.listJoinObj(was, ", ", it -> it));
        String t2;
        try {
            t2 = Core.listGet(Core.mapBuilderRemove(builder2, "El Salvador"), 0);
        } catch (MissingKey ignored3) {
            t2 = "Gone";
        }
        console.log(t2);
        String t3;
        try {
            Core.mapBuilderRemove(builder2, "Belize");
            t3 = "removed";
        } catch (MissingKey e) {
            t3 = e.getMessage();
        }
        console.log(t3);
        Map<String, List<String>> neighbors = Core.mappedToMap(builder2);
        console.log("From built: " + Core.listJoinObj(Core.mappedGet(neighbors, "Nicaragua"), ", ", it -> it));
        String t4;
        try {
            t4 = lookUp(302);
        } catch (MissingKey e2) {
            t4 = e2.getMessage();
        }
        console.log(t4);
        Map<String, Integer> fromBuilder = new LinkedHashMap<>();
        int t5;
        try {
            t5 = Core.mappedGet(fromBuilder, "nope");
        } catch (MissingKey ignored4) {
            t5 = -1;
        }
        console.log(Integer.toString(t5));
        Map<Integer, Integer> mapOfNone = Core.mapConstructor(List.of());
        Map<String, String> mapOfOne = Core.mapConstructor(List.of(new SimpleImmutableEntry<>("jack", "all trades")));
        Map<Integer, String> mapOfTwo = Core.mapConstructor(List.of(new SimpleImmutableEntry<>(1, "one"), new SimpleImmutableEntry<>(2, "electric boogaloo")));
        console.log("mapOfNone: " + mapOfNone.size());
        console.log("mapOfOne: " + mapOfOne.size());
        console.log("mapOfTwo: " + mapOfTwo.size());
        eightColors = new LinkedHashMap<>();
        eightColors.put("black", List.of(false, false, false));
        eightColors.put("red", List.of(true, false, false));
        eightColors.put("green", List.of(false, true, false));
        eightColors.put("blue", List.of(false, false, true));
        eightColors.put("yellow", List.of(true, true, false));
        eightColors.put("cyan", List.of(false, true, true));
        eightColors.put("purple", List.of(true, false, true));
        eightColors.put("white", List.of(true, true, true));
        console.log("with 3 bits you can draw " + eightColors.size() + " colors.");
        BiConsumer<String, List<Boolean>> fn9 = (color, bits) -> {
            List<Boolean> foundByGetOr = eightColors.getOrDefault(color, List.of());
            if (foundByGetOr.size() != 3) {
                console.log("getOr(\"" + color + "\") failed to find key");
            }
            String want = colorToHex(bits);
            String got = colorToHex(foundByGetOr);
            if (!want.equals(got)) {
                console.log("getOr(\"" + color + "\") found " + got + " but wanted " + want);
            }
        };
        Core.mappedForEach(eightColors, fn9);
        Map<String, String> map = Core.mapConstructor(List.of(new SimpleImmutableEntry<>("key", "value"), new SimpleImmutableEntry<>("other key", "other value")));
        printMap("map", map);
        Map<String, String> builder = new LinkedHashMap<>(map);
        builder.put("added key", "added value");
        builder.put("key", "replaced value");
        printMapBuilder("builder", builder);
        printMap("map (same as above)", map);
        Map<String, String> newMap = Core.mappedToMap(builder);
        printMap("builder to map", newMap);
        Map<String, String> builderAsMapped = builder;
        Map<String, String> newMapFromMapped = Core.mappedToMap(builderAsMapped);
        Core.mapBuilderRemove(builder, "other key");
        builder.put("key", "double replaced value");
        printMap("builder to map (same as above)", newMap);
        printMap("builder to map via mapped (same as above)", newMapFromMapped);
        builder.clear();
        builder.put("something", "alone");
        printMapBuilder("recently cleared", builder);
        Map<String, String> mayOrMayNotCopyMap = Core.mappedToMap(map);
        Map<String, String> mapped = map;
        Map<String, String> mayOrMayNotCopyMapped = Core.mappedToMap(mapped);
        Map<String, String> madeBy = new LinkedHashMap<>();
        madeBy.put("temper", "temper systems");
        printMapBuilder("made by", madeBy);
        Map<String, String> madeByAndJokes = new LinkedHashMap<>(madeBy);
        madeByAndJokes.put("milk", "milking a cow");
        printMapBuilder("made by (and a joke about milk)", madeByAndJokes);
        printMapBuilder("made by (not changed from a change to a copy)", madeBy);
    }
}
