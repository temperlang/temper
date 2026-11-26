package classes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class ClassesGlobal {
    private ClassesGlobal() {}

    static final Console console;

    static @Nullable Loose maybeLoose(int n) {
        if (n == 0) {
            return null;
        } else {
            return new Loose(n);
        }
    }

    static Shape shape(Shape x) {
        return x;
    }

    static Theme picked(Theme t, String why) {
        console.log(why);
        return t;
    }

    static String noted(String s) {
        console.log(s);
        return s;
    }

    static String renamed(Tally t, String name) {
        t.count = t.count + 10;
        return name;
    }

    static String compared(BiPredicate<@Nullable Loose, @Nullable Loose> eq) {
        List<Integer> ns = List.of(0, 1, -1, 2);
        List<String> out = new ArrayList<>();
        {
            int length = ns.size();
            int i = 0;
            while (i < length) {
                int a = Core.listGet(ns, i);
                i = i + 1;
                {
                    int length2 = ns.size();
                    int i2 = 0;
                    while (i2 < length2) {
                        int b = Core.listGet(ns, i2);
                        i2 = i2 + 1;
                        out.add(eq.test(maybeLoose(a), maybeLoose(b)) ? "T" : "f");
                    }
                }
            }
        }
        return Core.listJoinObj(out, "", s -> s);
    }

    static Class<?> typeNamed(int n) {
        if (n == 0) {
            return Void.class;
        } else if (n == 1) {
            return Boolean.class;
        } else if (n == 2) {
            return Integer.class;
        } else if (n == 3) {
            return Long.class;
        } else if (n == 4) {
            return Double.class;
        } else {
            return String.class;
        }
    }

    static Class<?> same(Class<?> t) {
        return t;
    }

    static <T> T orFail(boolean ok, T x, Failing<T> f) {
        if (ok) {
            return x;
        } else {
            return f.fail();
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("classes"));
        Rect r = new Rect(2, 3);
        console.log(r.toString());
        console.log(Integer.toString(r.getArea()));
        Shape s = shape(r);
        console.log(s.describe());
        Shape q = shape(new Square(4));
        console.log(q.describe());
        console.log(Integer.toString(q.getArea()));
        console.log(Integer.toString(r.scaled(2).scaled(3).getArea()));
        console.log(Rect.unit.scaled(5).toString());
        Counter c = Counter.zero();
        c.bump().bump();
        console.log(Integer.toString(c.count));
        c.setDoubled(10);
        console.log(Integer.toString(c.getDoubled()));
        console.log(Boolean.toString(c.same(new Counter().bump().bump())));
        c.setAdded(5);
        int t = 2;
        c.setAdded(t);
        int t2 = t;
        c.setAdded(t2);
        int added = t2;
        console.log(added + " " + c.count);
        console.log(new Point(1, 2).eq(new Point(1, 2)) + " " + !new Point(1, 2).eq(new Point(2, 1)));
        console.log(Boolean.toString(new Holder(new Loose(3), "a").eq(new Holder(new Loose(-3), "a"))));
        Loose loose = new Loose(2);
        String t3 = loose.mirrors(new Loose(-2)) + " ";
        @Nullable Loose left = maybeLoose(-2);
        String t4 = t3 + (left == null ? false : left.eq(loose)) + " ";
        Loose left2 = loose;
        @Nullable Loose right = maybeLoose(0);
        String t5 = t4 + (right == null ? false : left2.eq(right)) + " ";
        @Nullable Loose left3 = maybeLoose(0);
        console.log(t5 + !(left3 == null ? false : left3.eq(loose)));
        @Nullable Loose subject = maybeLoose(-2);
        String matched = (subject == null ? false : subject.eq(loose)) ? "loose" : "other";
        console.log(matched);
        Boxed<String> b1 = new Boxed<String>("x", 1);
        Boxed<String> b3 = new Boxed<String>("z", 2);
        console.log(b1.value + " " + b1.eq(new Boxed<String>("y", 1)) + " " + !b1.eq(b3) + " " + b1.sameAs(new Boxed<String>("y", 1)) + " " + b3.sameAs(b1));
        console.log((new Version(1, 2).compareTo(new Version(1, 3)) < 0) + " " + (new Version(2, 0).compareTo(new Version(1, 9)) >= 0));
        console.log((q instanceof Square) + " " + (s instanceof Square));
        Square sq = (Square) q;
        console.log(Integer.toString(sq.getArea()));
        if (s instanceof Rect) {
            console.log(Integer.toString(((Rect) s).w));
        }
        Stock st = new Stock(List.of(4, 5));
        st.setItems(List.of(1, 2, 3));
        console.log(st.count + " " + st.size + " " + st.sizeNow() + " " + st.getItems().size());
        Theme plain = new Theme("black", "white", 12, false);
        Theme spread = plain;
        Theme loud = new Theme(spread.fg, spread.bg, 20, true, "!");
        Theme spread2 = loud;
        Theme dark = new Theme(plain.bg, plain.fg, spread2.size, spread2.bold, spread2.note);
        console.log(plain.toString() + " | " + loud.toString() + " | " + dark.toString());
        Theme spread3 = picked(dark, "old");
        String arg = spread3.bg;
        String arg2 = noted("red");
        boolean arg3 = spread3.bold;
        @Nullable String arg4 = spread3.note;
        Theme mixed = new Theme(arg2, arg, 9, arg3, arg4);
        console.log(mixed.toString());
        Theme spread4 = picked(plain, "first");
        Theme ordered = new Theme(noted("second"), noted("third"), spread4.size, spread4.bold, spread4.note);
        console.log(ordered.toString());
        Tally first = new Tally(1, "a");
        Tally spread5 = first;
        Tally second = new Tally(spread5.count, renamed(first, "b"));
        console.log(first.count + " " + second.count + " " + second.name);
        console.log(compared((a, b) -> a == null ? b == null : b == null ? false : a.eq(b)));
        console.log(compared((a, b) -> !(a == null ? b == null : b == null ? false : a.eq(b))));
        console.log(compared((a, b) -> b == null ? a == null : a == null ? false : b.eq(a)));
        BiPredicate<@Nullable Loose, @Nullable Loose> fn4 = (a, b) -> {
            if (a == null) {
                return false;
            }
            if (b == null) {
                return false;
            }
            return a.mirrors(b);
        };
        console.log(compared(fn4));
        Class<?> favorite = same(Long.class);
        int named = 0;
        {
            int i = 0;
            while (i < 6) {
                typeNamed(i);
                named = named + 1;
                i = i + 1;
            }
        }
        console.log(named + " types");
        Sql sql = new Sql();
        sql.appendString("it");
        sql.appendString(null);
        sql.appendInt(3);
        sql.appendShape(new Square(2));
        sql.appendRect(new Rect(1, 2));
        console.log(sql.getAccumulated());
        int seven = 7;
        Sql accumulator = new Sql();
        accumulator.appendSafe("SELECT ");
        accumulator.appendInt(seven);
        accumulator.appendSafe(", ");
        accumulator.appendString("x");
        accumulator.appendSafe(" FROM ");
        accumulator.appendRect(new Rect(2, 2));
        console.log(accumulator.getAccumulated());
        Limited stepper = new Stepper(3);
        console.log("next " + stepper.next(1));
        console.log(orFail(true, 4, new IntFailing()) + " " + orFail(true, "s", new AnyFailing<String>()));
    }
}
