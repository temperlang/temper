package temper.std;
import temper.core.Core;
public class StdMain {
    private StdMain() {}
    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Class.forName("temper.std.StdGlobal");
    }
}