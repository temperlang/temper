package lang.temper.interp

import lang.temper.env.ChildEnvironment
import lang.temper.env.Environment
import lang.temper.interp.docgenalts.altDocGenEnv
import lang.temper.lexer.Genre
import lang.temper.name.TemperName
import lang.temper.value.Value

/** A blank, mutable environment. */
fun blankEnvironment(parent: Environment): ChildEnvironment =
    BlockEnvironment(parent)

/**
 * An environment that includes only pure builtins.
 * You probably want `builtinEnvironment` with implicits rather than this.
 * */
fun builtinOnlyEnvironment(
    parent: Environment,
    genre: Genre,
): Environment {
    val env = BuiltinEnvironment(parent)
    return when (genre) {
        Genre.Library -> env
        Genre.Documentation -> altDocGenEnv(env)
    }
}

fun immutableEnvironment(
    parent: Environment,
    nameToValue: Map<TemperName, Value<*>>,
    isLongLived: Boolean,
): Environment = ImmutableEnvironment(parent, nameToValue, isLongLived = isLongLived)
