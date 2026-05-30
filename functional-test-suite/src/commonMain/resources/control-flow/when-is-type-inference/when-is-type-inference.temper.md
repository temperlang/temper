# `when` with `is TypeName` arms infers the arm type

A `when` expression whose arms use `is TypeName` patterns should produce
a type reflecting the common type of the arms, not `Void`.

Regression test for https://github.com/temperlang/temper/issues/427

## Setup — sealed interface with two variants

    export sealed interface Shape {}
    export class Circle() extends Shape {}
    export class Square() extends Shape {}

## Case 1: arms return String

`when` without `else` should still type as `String | Void`, not `Void`.
The workaround (if-else narrowing) is shown alongside for comparison.

    export let describe(s: Shape): String {
      when (s) {
        is Circle -> "circle";
        is Square -> "square";
      }
    }

    export let describeIfElse(s: Shape): String {
      if (s is Circle) { "circle" } else { "square" }
    }

    test("when arms return String — tail expression") {
      assert(describe(new Circle()) == "circle") { "when: circle" };
      assert(describe(new Square()) == "square") { "when: square" };
      assert(describeIfElse(new Circle()) == "circle") { "if-else: circle" };
      assert(describeIfElse(new Square()) == "square") { "if-else: square" };
    }

## Case 2: arms return sealed-interface subtypes

    export sealed interface Status {}
    export class Active()                    extends Status {}
    export class Done(public winner: String) extends Status {}
    export class Drawn()                     extends Status {}

    export let classify(code: Int): Status {
      when (code) {
        0    -> new Active();
        1    -> new Done("white");
        else -> new Drawn();
      }
    }

    test("when arms return sealed subtypes — inferred as common interface") {
      assert(classify(0) is Active) { "active" };
      assert(classify(1) is Done)   { "done" };
      assert(classify(2) is Drawn)  { "drawn" };
    }

## Case 3: nested sealed dispatch

    export sealed interface Expr {}
    export class Lit(public value: Int) extends Expr {}
    export class Neg(public inner: Expr) extends Expr {}

    export let eval(e: Expr): Int {
      when (e) {
        is Lit -> e.value;
        is Neg -> -eval(e.inner);
      }
    }

    test("recursive when dispatch over sealed Expr") {
      assert(eval(new Lit(5)) == 5)         { "lit 5" };
      assert(eval(new Neg(new Lit(3))) == -3) { "neg(lit 3)" };
      assert(eval(new Neg(new Neg(new Lit(7)))) == 7) { "neg(neg(lit 7))" };
    }
