# Base

An interface whose getters and methods have bodies.

    export interface Named {
      public get name(): String;
      public get shout(): String { "${name}!" }
      public greet(other: String, punct: String = "."): String { "hello ${other}, I am ${name}${punct}" }
      public tag(): String { "base" }
      public get nick(): String { initial(name) }
      public set nick(value: String): Void { console.log("cannot rename ${name} to ${value}"); }
      private initial(s: String): String { "${s}~" }
    }

    export interface Box<T> {
      public get item(): T;
      public both(): List<T> { [item, item] }
    }
