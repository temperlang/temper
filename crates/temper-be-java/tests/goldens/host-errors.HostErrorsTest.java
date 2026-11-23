package host_errors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import temper.core.Bubble;
import temper.core.Core;
import temper.std.regex.CodePoints;
import temper.std.testing.Test;

public final class HostErrorsTest {
    static int at(int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return i;
    }

    static List<Integer> built() {
        List<Integer> lb = new ArrayList<>();
        lb.add(1);
        return lb;
    }

    static int read(List<Integer> ls, int i) {
        return Core.listGet(ls, at(i));
    }

    static int write(List<Integer> lb, int i) {
        Core.listSet(lb, at(i), 3);
        return lb.size();
    }

    static int insert(List<Integer> lb, int i) {
        Core.listAdd(lb, 2, at(i));
        return lb.size();
    }

    static int removeLast(List<Integer> lb, int i) {
        at(i);
        return Core.listRemoveLast(lb);
    }

    static int removeFirst(Deque<Integer> d, int i) {
        at(i);
        return Core.dequeRemoveFirst(d);
    }

    static int sum(List<Integer> ls, int i) {
        at(i);
        return Core.listedReduceInt(ls, (a, b) -> a + b);
    }

    static int codeAt(String s, int i) {
        at(i);
        return Core.stringGet(s, s.length());
    }

    static String replaced(int i) {
        at(i);
        return new CodePoints("b").compiled().replace("abc", match -> Core.listGet(List.of("x"), i));
    }

    static {
        Bubble.keepStackTraces();
    }

    @org.junit.jupiter.api.Test
    public void listRead() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = read(List.of(1, 2), 5);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void listReadAtTheEnd() {
        Test test = new Test();
        Throwable failure = null;
        try {
            List<Integer> ls = List.of(1, 2);
            int r;
            try {
                r = read(ls, ls.size());
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void negativeIndex() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = read(built(), -1);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void set() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = write(built(), 5);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void setAtTheEnd() {
        Test test = new Test();
        Throwable failure = null;
        try {
            List<Integer> lb = built();
            int r;
            try {
                r = write(lb, lb.size());
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void insert() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = insert(built(), 5);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void insertPastTheEnd() {
        Test test = new Test();
        Throwable failure = null;
        try {
            List<Integer> lb = built();
            int r;
            try {
                r = insert(lb, lb.size() + 1);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void insertAtTheEndAppends() {
        Test test = new Test();
        Throwable failure = null;
        try {
            List<Integer> lb = built();
            int n;
            try {
                n = insert(lb, lb.size());
            } catch (Bubble ignored) {
                n = -1;
            }
            test.assert_(n == 2 && Core.listGet(lb, 1) == 2, () -> "didn't append");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void removeLast() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = removeLast(new ArrayList<>(), 0);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void removeFirst() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = removeFirst(new ArrayDeque<>(), 0);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void reduce() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = sum(List.of(), 0);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void stringRead() {
        Test test = new Test();
        Throwable failure = null;
        try {
            int r;
            try {
                r = codeAt("ab", 0);
            } catch (Bubble ignored) {
                r = -1;
            }
            test.assert_(r != -1, () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void replaceCallback() {
        Test test = new Test();
        Throwable failure = null;
        try {
            String r;
            try {
                r = replaced(3);
            } catch (Bubble ignored) {
                r = "";
            }
            test.assert_(!r.equals(""), () -> "caught");
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
