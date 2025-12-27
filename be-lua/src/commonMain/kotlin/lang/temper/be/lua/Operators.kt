@file:Suppress("unused")

package lang.temper.be.lua

import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.Position

interface LuaAtomic<A : Lua.Tree> : TokenSerializable {
    fun atom(pos: Position): A
}

enum class BinaryOpEnum(
    private val token: String,
    val operatorDefinition: LuaOperatorDefinition,
) : LuaAtomic<Lua.BinaryOp> {
    BoolOr("or", LuaOperatorDefinition.Or),
    BoolAnd("and", LuaOperatorDefinition.And),
    Lt("<", LuaOperatorDefinition.Lt),
    Gt(">", LuaOperatorDefinition.Gt),
    Eq("==", LuaOperatorDefinition.Eq),
    GtEq(">=", LuaOperatorDefinition.Ge),
    LtEq("<=", LuaOperatorDefinition.Le),
    NotEq("~=", LuaOperatorDefinition.Ne),
    Concat("..", LuaOperatorDefinition.Concat),
    Dot(".", LuaOperatorDefinition.Dot),
    Add("+", LuaOperatorDefinition.Add),
    Sub("-", LuaOperatorDefinition.Sub),
    Mul("*", LuaOperatorDefinition.Mul),
    Div("/", LuaOperatorDefinition.Div),
    Mod("%", LuaOperatorDefinition.Mod),
    Pow("^", LuaOperatorDefinition.Pow),
    ;

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.infixOp(token)
    }

    override fun atom(pos: Position): Lua.BinaryOp = Lua.BinaryOp(pos, this, LuaOperatorDefinition.lookup(this.name)!!)
}

enum class UnaryOpEnum(private val token: String) : LuaAtomic<Lua.UnaryOp> {
    UnaryAdd("-"),
    UnarySub("#"),
    BoolNot("not"),
    ;

    override fun renderTo(tokenSink: TokenSink) {
        if (this == BoolNot) {
            tokenSink.word(token)
        } else {
            tokenSink.prefixOp(token)
        }
    }

    override fun atom(pos: Position): Lua.UnaryOp = Lua.UnaryOp(pos, this, LuaOperatorDefinition.lookup(this.name)!!)
}
