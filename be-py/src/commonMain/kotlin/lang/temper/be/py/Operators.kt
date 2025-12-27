@file:Suppress("unused")

package lang.temper.be.py

import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.Position

interface PyAtomic<A : Py.Tree> : TokenSerializable {
    fun atom(pos: Position): A
}

enum class BinaryOpEnum(private val token: String) : PyAtomic<Py.BinaryOp> {
    BoolOr("or"),
    BoolAnd("and"),
    Lt("<"),
    Gt(">"),
    Eq("=="),
    GtEq(">="),
    LtEq("<="),
    NotEq("!="),
    In("in"),
    NotIn("not in"),
    Is("is"),
    IsNot("is not"),
    BitwiseOr("|"),
    BitwiseXor("^"),
    BitwiseAnd("&"),
    ShiftLeft("<<"),
    ShiftRight(">>"),
    Add("+"),
    Sub("-"),
    Mult("*"),
    MatMult("@"),
    Div("/"),
    Mod("%"),
    FloorDiv("//"),
    Pow("**"),
    ;

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.infixOp(token)
    }

    override fun atom(pos: Position): Py.BinaryOp = Py.BinaryOp(pos, this, PyOperatorDefinition.lookup(this.name)!!)
}

enum class AugAssignOpEnum(private val token: String) : PyAtomic<Py.AugAssignOp> {
    BitwiseOr("|="),
    BitwiseXor("^="),
    BitwiseAnd("&="),
    ShiftLeft("<<="),
    ShiftRight(">>="),
    Add("+="),
    Sub("-"),
    Mult("*="),
    MatMult("@="),
    Div("/="),
    Mod("%="),
    FloorDiv("//="),
    Pow("**="),
    ;

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.infixOp(token)
    }

    override fun atom(pos: Position): Py.AugAssignOp = Py.AugAssignOp(pos, this)
}

enum class UnaryOpEnum(private val token: String) : PyAtomic<Py.UnaryOp> {
    UnaryAdd("+"),
    UnarySub("-"),
    UnaryInvert("~"),
    BoolNot("not"),
    ;

    override fun renderTo(tokenSink: TokenSink) {
        if (this == BoolNot) {
            tokenSink.word(token)
        } else {
            tokenSink.prefixOp(token)
        }
    }

    override fun atom(pos: Position): Py.UnaryOp = Py.UnaryOp(pos, this, PyOperatorDefinition.lookup(this.name)!!)
}
