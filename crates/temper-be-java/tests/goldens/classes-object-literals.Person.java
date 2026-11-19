package object_literals;

import java.util.List;
import object_literals.extra_class.Stringable;
import temper.core.Core;
import temper.core.Nullable;

public final class Person implements Stringable {
    public final String name;
    public final int age;

    public Person(String name, @Nullable Integer age) {
        int age2 = age != null ? age : -100;
        this.name = name;
        this.age = age2;
    }

    public Person(String name) {
        this(name, null);
    }

    public String toString() {
        String ageText = Core.listJoinInt(List.<Integer>of(0), "", unused -> Integer.toString(this.age));
        return "Person " + this.name + " of age " + ageText;
    }

    public boolean eq(Person other) {
        if (this.name.equals(other.name)) {
            return this.age == other.age;
        } else {
            return false;
        }
    }

    public String getName() {
        return this.name;
    }

    public int getAge() {
        return this.age;
    }

    public static final class Builder {
        String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Nullable Integer age;

        public Builder age(@Nullable Integer age) {
            this.age = age;
            return this;
        }

        public Person build() {
            if (name == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (name == null) {
                    _message.append(" name");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Person(name, age);
        }
    }
}
