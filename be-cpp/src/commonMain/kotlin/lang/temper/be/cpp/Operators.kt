@file:Suppress("unused")

package lang.temper.be.cpp

import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.Position

interface CppAtomic<A : Cpp.Tree> : TokenSerializable {
    fun atom(pos: Position): A
}

enum class BinaryOpEnum(
    private val token: String,
    val operatorDefinition: CppOperatorDefinition,
) : CppAtomic<Cpp.BinaryOp> {
    Dot(".", CppOperatorDefinition.Dot),
    Arrow("->", CppOperatorDefinition.Arrow),
    SizeOf("sizeof", CppOperatorDefinition.SizeOf),
    CoAwait("co_await", CppOperatorDefinition.CoAwait),
    New("new", CppOperatorDefinition.New),
    Delete("delete", CppOperatorDefinition.Delete),
    DotDeref(".*", CppOperatorDefinition.DotDeref),
    ArrowDeref("->*", CppOperatorDefinition.ArrowDeref),
    Mul("*", CppOperatorDefinition.Mul),
    Div("/", CppOperatorDefinition.Div),
    Mod("%", CppOperatorDefinition.Mod),
    Add("+", CppOperatorDefinition.Add),
    Sub("-", CppOperatorDefinition.Sub),
    Shl("<<", CppOperatorDefinition.Shl),
    Shr(">>", CppOperatorDefinition.Shr),
    Compare("<=>", CppOperatorDefinition.Compare),
    Lt("<", CppOperatorDefinition.Lt),
    Gt(">", CppOperatorDefinition.Gt),
    Le("<=", CppOperatorDefinition.Le),
    Ge(">=", CppOperatorDefinition.Ge),
    Eq("==", CppOperatorDefinition.Eq),
    Ne("!=", CppOperatorDefinition.Ne),
    BitAnd("&", CppOperatorDefinition.BitAnd),
    BitXor("^", CppOperatorDefinition.BitXor),
    BitOr("|", CppOperatorDefinition.BitOr),
    And("&&", CppOperatorDefinition.And),
    Or("||", CppOperatorDefinition.Or),
    Ternary("?:", CppOperatorDefinition.Ternary),
    Throw("throw", CppOperatorDefinition.Throw),
    CoYield("co_yield", CppOperatorDefinition.CoYield),
    Assign("=", CppOperatorDefinition.Assign),
    AddAssign("+=", CppOperatorDefinition.AddAssign),
    SubAssign("-=", CppOperatorDefinition.SubAssign),
    MulAssign("*=", CppOperatorDefinition.MulAssign),
    DivAssign("/=", CppOperatorDefinition.DivAssign),
    ModAssign("%=", CppOperatorDefinition.ModAssign),
    ShlAssign("<<=", CppOperatorDefinition.ShlAssign),
    ShrAssign(">>=", CppOperatorDefinition.ShrAssign),
    AndAssign("&=", CppOperatorDefinition.AndAssign),
    XorAssign("^=", CppOperatorDefinition.XorAssign),
    OrAssign("|=", CppOperatorDefinition.OrAssign),
    Comma(",", CppOperatorDefinition.Comma),
    ;

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.infixOp(token)
    }

    override fun atom(pos: Position): Cpp.BinaryOp = Cpp.BinaryOp(pos, this)
}

enum class UnaryOpEnum(
    private val token: String,
    val operatorDefinition: CppOperatorDefinition,
) : CppAtomic<Cpp.UnaryOp> {
    PreInc("++", CppOperatorDefinition.PreInc),
    PreDec("--", CppOperatorDefinition.PreDec),
    Plus("+", CppOperatorDefinition.Plus),
    Minus("-", CppOperatorDefinition.Minus),
    Not("!", CppOperatorDefinition.Not),
    BitNot("~", CppOperatorDefinition.BitNot),
    Deref("*", CppOperatorDefinition.Deref),
    AddrOf("&", CppOperatorDefinition.AddrOf),
    ;

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.prefixOp(token)
    }

    override fun atom(pos: Position): Cpp.UnaryOp = Cpp.UnaryOp(pos, this)
}
