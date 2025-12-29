package lang.temper.cst

import lang.temper.common.console
import lang.temper.common.logIf
import lang.temper.lexer.Associativity
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.lexer.TokenType

private const val DEBUG = false

// Allow debugging which branch was ultimately chosen.
private fun branch(description: String, output: Boolean): Boolean {
    console.logIf(DEBUG) { "  branch $description -> $output" }
    return output
}

/**
 * Given operators, true when an operator stack consisting of outers
 * can contain the inner operator.
 *
 * @param child a stack element.
 * @param parent the stack element that would contain [child].
 * @param grandParent a stack element that would contain [parent] or null if [parent] is
 *     [root][Operator.Root].
 */
fun canNest(
    grandParent: OperatorStackElement?,
    parent: OperatorStackElement,
    child: OperatorStackElement,
): Boolean {
    console.logIf(DEBUG) {
        "canNest(${
            if (grandParent == null) {
                "<null>"
            } else {
                "${grandParent.operator}+${grandParent.childCount}+${
                    grandParent.eventualChildCount
                }"
            }
        }, ${parent.operator}+${parent.childCount}+${parent.eventualChildCount
        }, ${child.operator}+${child.childCount})"
    }
    return when {
        // The root can't nest in anything.
        child.operator == Operator.Root -> branch("in root", false)

        child.tokenType == TokenType.QuotedString ->
            branch("string content quoted", parent.operator == Operator.QuotedGroup)

        // This allows for interstitials between statements like the below:
        //
        //     statement_before;
        //
        //     mixin ("label"):
        //
        //     statement_after;
        //
        // That does not collide with property names in property bag syntax.
        //
        // This syntax is reserved for function mixins pending finalization of
        // our user macro strategy.
        parent.operator.text == Operator.SelectivePostColon.text && isMixinMarker(child) ->
            branch(
                "only selective colon grabs marker word",
                parent.operator == Operator.SelectivePostColon,
            )

        // label : only occur in blocks or at the top level.
        parent.operator == Operator.LowColon && !isBlockLike(grandParent?.operator) ->
            branch("colon !in blockLike", false)

        // Match when we're considering the first operand to a `label: statement` operation.
        parent.operator == Operator.LowColon && parent.childCount == 0 && // label and ":"
            parent.eventualChildCount == 0 ->
            branch(
                "label:",
                when (child.operator) {
                    Operator.DollarCurly -> true // Allow quasi holes for labels
                    Operator.Leaf ->
                        // Use LowColon for `label:` but not `let x:`.
                        child.childCount == 1
                    else -> false // Fail over to Operator.InfixColon
                },
            )

        // SelectivePostColon only applies to a few leaf children.
        parent.operator == Operator.SelectivePostColon -> branch("SelectivePostColonBanned", false)

        // `case` prefix operators are allowed in several situations:
        // -                    parent=Curly,         child=Case
        // - grandParent=Curly, parent=Semi,          child=Case
        // -                    parent=Comma,         child=Case
        // -                    parent=ThinArrow+0+0, child=Case     infix `->` adopts case as left operand
        child.operator == Operator.PreCase -> branch(
            "SelectiveCaseInLeftOfMatch",
            when {
                parent.operator == Operator.Curly -> true
                parent.operator == Operator.Semi && grandParent?.operator == Operator.Curly -> true
                parent.operator == Operator.Comma -> true
                parent.operator == Operator.ThinArrow -> parent.childCount == 0
                else -> false
            },
        )

        // `extends` with commas is not allowed inside angle brackets or comma lists.
        child.operator in extendsLikeCommaOperators &&
            (
                parent.operator == Operator.Comma ||
                    (parent.operator == Operator.Angle && parent.childCount >= 2)
                ) ->
            branch("ExtendsComma", false)
        // `extends` with commas is not allowed inside a no-comma operator from the same family.
        child.operator in extendsLikeCommaOperators && parent.operator in extendsLikeNoCommaOperators ->
            branch("ExtendsCommaInNoComma", false)

        // Open brackets can contain anything.
        // Completed bracket operators can't contain anything.
        parent.operator.closer && parent.isNotEmpty() ->
            branch("reset on open bracket", needsCloseBracket(parent))

        // new is a prefix operator, but we want function application
        // to apply to it as a whole, not just its type argument.
        // This avoids problems over function application having high
        // precedence, but type operators like <...> having lower precedence.
        child.operator == Operator.New && child.isNotEmpty() &&
            (parent.operator == Operator.Paren || parent.operator == Operator.Curly) &&
            parent.isEmpty() ->
            branch("new", true)

        // `@` is a prefix operator that can take two arguments.  When there are two adjacent
        // parenthetical grouping following it, as in
        //     @A() (...)
        // we want that to be treated as `@` applied to two things:
        // - A use of the paren operator `A()`
        // - A use of grouping parens `(...)`
        // so that decorator syntax can be used to allow an annotation to be applied to an
        // arbitrary expression.
        //
        // Without this rule, the above would be interpreted as `@` applied to
        // - a call to the result of `A()` with argument list `(...)`
        grandParent?.operator == Operator.At && grandParent.eventualChildCount == 2 &&
            parent.operator == Operator.Paren && child.operator == Operator.Paren ->
            branch("@ with parentheses", false)

        // For code like `let f(): ReturnType { body }
        // we need to allow curly brackets to contain the colon operator.
        parent.operator == Operator.Curly && child.operator == Operator.HighColon &&
            parent.childCount == 0 && child.childCount >= 2 ->
            branch("curlies over colon", true)
        // Similarly, in code like `class C extends Supers {}
        parent.operator == Operator.Curly && child.operator in extendsLikeCommaOperators &&
            parent.childCount == 0 && child.childCount >= 2 ->
            branch("curlies over extends", true)
        // Similarly, in code like `class C extends Supers {}
        parent.operator in extendsLikeCommaOperators && child.operator == Operator.Curly ->
            branch("extends not over curlies", false)

        // In `new C<T>` the should `<T>` apply to the `C`, not `new C`,
        // so that the argument to `new` is a whole type.
        // When the call is outside, we can treat `new` as an operator that extracts
        // a factory for a type.
        parent.operator == Operator.Angle && child.operator == Operator.New &&
            parent.childCount == 0 && child.childCount != 0 ->
            branch("angle applies to type, not `new`", false)

        // Normal operator precedence comparison.
        parent.operator.precedence < child.operator.precedence ->
            branch("higher precedence", true)

        // Handle associative operators.
        parent.operator.precedence == child.operator.precedence &&
            (
                parent.operator.associativity != Associativity.Right ||
                    // Parser calls canNest twice for infix operators since
                    // it inserts between existing elements of the operator
                    // stack, but we only need to check associativity once.
                    (child.operator.operatorType == OperatorType.Infix && child.isEmpty())
                ) ->
            branch("same precedence, not right associative", true)

        else -> branch("else", false)
    }
}

private fun isBlockLike(operator: Operator?) = when (operator) {
    null,
    Operator.Root,
    Operator.Semi,
    Operator.SemiSemi,
    Operator.SemiSemiSemi,
    Operator.Arrow,
    Operator.Curly,
    Operator.CurlyGroup,
    -> true
    else -> false
}

private fun isMixinMarker(element: OperatorStackElement): Boolean {
    if (element.operator != Operator.Paren) { return false }
    if (element.childCount < 2) { return false }
    // Look for `mixin (`
    if (element.child(1).tokenText != "(") { return false }
    val callee = element.child(0)
    return callee.operator == Operator.Leaf && callee.childCount == 1 &&
        // TODO: If we use this, treat it as a reserved word
        callee.child(0).tokenText == "mixin"
}

private val extendsLikeCommaOperators = setOf(
    Operator.ExtendsComma, Operator.ImplementsComma, Operator.ForbidsComma, Operator.SupportsComma,
)

private val extendsLikeNoCommaOperators = setOf(
    Operator.ExtendsNoComma, Operator.ImplementsNoComma, Operator.ForbidsNoComma, Operator.SupportsNoComma,
)

val extendsLikeOperators = extendsLikeCommaOperators + extendsLikeNoCommaOperators
