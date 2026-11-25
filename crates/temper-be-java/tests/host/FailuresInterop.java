package interop;

import failures_interop.BadSign;
import failures_interop.FailuresInteropException;
import failures_interop.FailuresInteropGlobal;
import failures_interop.LookupFailed;
import failures_interop.NotFound;
import failures_interop.SignException;
import temper.core.Core;

/**
 * Host code using the generated {@code failures_interop} package's declared
 * failures the standard way, for {@code functional.rs}'s {@code runs}: it
 * catches them by their own types, the library's base, and a sealed
 * interface's abstract class, reads their fields, messages, and causes,
 * and lets a panic pass {@code catch (RuntimeException e)}. It prints a
 * line per check, which the test compares with what it expects. javac
 * compiles it with generated code's lints.
 */
public final class FailuresInterop {
    private FailuresInterop() {}

    /**
     * Runs the checks.
     *
     * @param args unused
     * @throws ClassNotFoundException never, as the class is generated
     */
    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            // Its top-level code logs.
            Class.forName("failures_interop.FailuresInteropGlobal");
        } finally {
            Core.endTopLevel();
        }

        try {
            FailuresInteropGlobal.lookup("z");
        } catch (NotFound e) {
            System.out.println("NotFound " + e.getKey() + ": " + e.getMessage());
        }

        try {
            FailuresInteropGlobal.lookup("y");
        } catch (FailuresInteropException e) {
            Object failure = e;
            System.out.println(
                "base " + (failure instanceof NotFound)
                    + ", RuntimeException " + (failure instanceof RuntimeException));
        }

        for (String text : new String[] {"x", ""}) {
            try {
                FailuresInteropGlobal.sign(text);
            } catch (SignException e) {
                System.out.println("sign " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (e instanceof BadSign && !((BadSign) e).getText().equals("x")) {
                    throw new AssertionError("text");
                }
            }
        }

        try {
            FailuresInteropGlobal.setting("port");
        } catch (LookupFailed e) {
            Throwable cause = e.getCause();
            System.out.println(
                "LookupFailed " + e.getMessage() + ", caused by "
                    + cause.getClass().getSimpleName() + " " + cause.getMessage());
        }

        try {
            FailuresInteropGlobal.madeBefore("early");
        } catch (LookupFailed e) {
            System.out.println("made before " + e.getMessage() + ", caused by " + e.getCause().getMessage());
        }

        try {
            FailuresInteropGlobal.thrownAgain("first");
        } catch (LookupFailed e) {
            System.out.println("thrown again " + e.getMessage() + ", caused by " + e.getCause().getMessage());
        }

        // A failure has its stack trace, from where it was made.
        try {
            FailuresInteropGlobal.lookup("w");
        } catch (NotFound e) {
            StackTraceElement top = e.getStackTrace()[0];
            System.out.println("trace " + top.getClassName() + "." + top.getMethodName());
        }

        try {
            try {
                FailuresInteropGlobal.risky(2);
            } catch (RuntimeException e) {
                System.out.println("panic caught");
            }
        } catch (AssertionError e) {
            System.out.println("panic passes");
        }
    }
}
