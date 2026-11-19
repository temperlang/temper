package object_literals;

import java.util.List;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;
import object_literals.extra_class.Circle;
import object_literals.extra_class.Rectangle;
import object_literals.extra_class.Stringable;
import temper.core.Core;
import temper.core.Core.Console;

public final class ObjectLiteralsGlobal {
    private ObjectLiteralsGlobal() {}

    static final Console console;

    static void bother() {
        Moreover whatIAm = new Moreover(1);
        Moreover moreover = new Moreover(2, 3);
        console.log("Moreover: " + moreover.toString());
        Moreover moreover2 = new Moreover(4);
        console.log("Moreover again: " + moreover2.toString());
        Moreover moreover3 = new Moreover(null, 7);
        console.log("Moreover yet again: " + moreover3.toString());
        Whatever whatever = new Whatever(5);
        console.log("Whatever: " + whatever.blah);
    }

    static {
        console = Core.getConsole(Logger.getLogger("object_literals"));
        Person person1 = new Person("Alice", 50);
        Person person2 = new Person("Bob", 45);
        Circle circle1 = new Circle(2);
        Circle circle2 = new Circle(1);
        List<Stringable> things = List.<Stringable>of(person1, person2, circle1, circle2);
        ToIntFunction<Stringable> fn = thing -> {
            console.log(thing.toString());
            return 0;
        };
        Core.listMapObjToInt(things, fn);
        new Whatever(456);
        new Whatever(321);
        new Rectangle(7, 8);
        bother();
        Whatever whatever = new Whatever(6);
        console.log("Whatever again: " + whatever.blah);
        Person personAgain = new Person("Carrie", 40);
        String name = personAgain.name;
        int someAge = personAgain.age;
        console.log("name is " + name + " and age is " + someAge);
        Person personMore = new Person(name, 35);
        console.log(personMore.toString());
        Person twin = new Person(person1.name, person1.age);
        console.log("Some are twins: " + person1.eq(twin));
        console.log("All are twins: " + person2.eq(twin));
    }
}
