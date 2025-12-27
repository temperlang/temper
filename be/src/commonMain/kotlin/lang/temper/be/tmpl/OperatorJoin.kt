package lang.temper.be.tmpl

import lang.temper.lexer.Associativity

/**
 * Given a binary operator like `||` and *n* arguments, the application of that operator to
 * successive pairs.
 */
fun <T> operatorJoin(
    /** The result of zero applications of the operator. */
    identity: T,
    elements: List<T>,
    /**
     * If left, then the operator is applied to [elements]\[0] and [elements]\[1] before the
     * operator is applied to the result of that and [elements]\[2]:
     *
     *     (elements[0] op elements[1]) op elements[2]
     *
     * Otherwise, it's parenthesized like
     *
     *     elements[0] op (elements[1] op elements[2])
     */
    associativity: Associativity,
    /** An application of the binary operator given two operands. */
    join: (T, T) -> T,
): T {
    var junction = identity
    when (associativity) {
        Associativity.Left ->
            for (element in elements) {
                junction = if (junction == identity) {
                    element
                } else {
                    join(junction, element)
                }
            }
        Associativity.Right ->
            // `||` and `&&` are right associative, so group like
            //     (cond0 || (cond1 || ...))
            // by generating inner right sides in reverse order.
            for (element in elements.asReversed()) {
                junction = if (junction == identity) {
                    element
                } else {
                    join(element, junction)
                }
            }
    }
    return junction
}
