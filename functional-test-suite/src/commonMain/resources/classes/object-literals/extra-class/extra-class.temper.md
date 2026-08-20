# Extra class definitions

We'll be using these classes from the main module.

First, we include an interface just to make things a bit more interesting.

    export interface Stringable {
      public toString(): String;
    }

Now create some classes, one with a single implicit constructor:

    export class Circle(
      public radius: Int,
    ) extends Stringable {
      public toString(): String {
        "Circle of radius ${radius}"
      }
    }

And another with custom constructors.

    export class Rectangle {
      public width: Int;
      public height: Int;

      public constructor(width: Int, height: Int) {
        this.width = width;
        this.height = height;
      }

      public constructor(squareWidth: Int) {
        this.width = squareWidth;
        this.height = squareWidth;
      }
    }
