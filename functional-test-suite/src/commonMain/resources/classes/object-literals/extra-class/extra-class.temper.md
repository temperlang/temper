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

    export class Rectangle(
      public width: Int,
      public height: Int,
    ) {

TODO: Once we have factory functions, expose this secondary one as
a factory and update ./object-literals.temper to uncomment the
property bag that uses it.

    //@factory public static createSquare(squareWidth: Int): Rectangle {
    //  ({ width: squareWidth, height: squareWidth })
    //}

    }
