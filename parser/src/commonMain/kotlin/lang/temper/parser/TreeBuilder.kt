package lang.temper.parser

import lang.temper.lexer.Operator
import lang.temper.lexer.TemperToken
import lang.temper.log.CodeLocation

/**
 * Abstracts away dealing with a source of tokens that are being lifted into a parse tree.
 *
 * For example, consider the following tokens
 *
 *           "1"   "+"   "2"
 *
 * Processing starts by inserting [start][TreeBuilder.start] marks that correspond to open parentheses
 * and [finish][TreeBuilder.finish] marks that correspond to close parentheses.
 *
 *       ( ( "1" ) "+" ( "2" ) )
 *
 * which can then be lifted into a tree
 *
 *           "1"         "2"
 *         (     ) "+" (     )
 *       (                     )
 *
 * There is an implicit surrounding pair of parentheses
 *
 *           "1"         "2"
 *         (     ) "+" (     )
 *       (                     )
 *     (                         )
 *
 * Each close parenthesis also includes information about the kind of tree.
 *
 *           "1"         "2"
 *         ( Leaf) "+" ( Leaf)
 *       (                 Plus)
 *     (                     Root)
 *
 * The sequence of calls to parse this is
 *
 * | Call             | Notes                | Output                                      |
 * | ---------------- | -------------------- | ------------------------------------------- |
 * | [start]\()       | initialize the root  | (                                           |
 * | [start]\()       |                      | ( (                                         |
 * | [leaf]\("1")     | to contribute "1"    | ( ( "1"                                     |
 * | [startBefore]\() | since "+" is infix   | ( ( ( "1"                                   |
 * | [finish]\(Leaf)  | to close the number  | ( ( ( "1" Leaf)                             |
 * | [leaf]\("+")     | to consume "+"       | ( ( ( "1" Leaf) "+"                         |
 * | [start]\()       | next operand in Plus | ( ( ( "1" Leaf) "+" (                       |
 * | [leaf]\("2")     | to contribute "2"    | ( ( ( "1" Leaf) "+" ( "2"                   |
 * | [finish]\(Leaf)  | close out the stack  | ( ( ( "1" Leaf) "+" ( "2" Leaf)             |
 * | [finish]\(Plus)  | close out the stack  | ( ( ( "1" Leaf) "+" ( "2" Leaf) Plus)       |
 * | [finish]\(Root)  | close out the stack  | ( ( ( "1" Leaf) "+" ( "2" Leaf) Plus) Root) |
 *
 * @param <T> a type for representing open parentheses.
 */
interface TreeBuilder<T> {
    /** Describes the source of tokens. */
    val codeLocation: CodeLocation

    /**
     * Called to indicate that processing of the current token is done and that it falls within
     * the topmost pair of parentheses.
     */
    fun leaf(token: TemperToken)

    /**
     * Called to contribute a close parenthesis corresponding to the innermost open parenthesis.
     *
     * @param x the value returned by [start] or [startBefore] when the innermost open parenthesis
     *     was specified.
     * @param operator the finished tree's operator.
     */
    fun finish(x: T, operator: Operator)

    /** Called to contribute an open parenthesis to the innermost open parenthesis. */
    fun start(): T

    /**
     * Called to indicate that the innermost open parenthesis is actually inside another
     * parenthesis
     *
     * This is called when an infix operation is found.
     * For example, when parsing
     *
     *     "1" "+" "2"
     *
     * We need to start the parenthesis for the `+` operation before the first operand.
     */
    fun startBefore(x: T): T
}
