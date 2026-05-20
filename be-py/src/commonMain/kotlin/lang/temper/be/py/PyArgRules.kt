package lang.temper.be.py

fun callArgsValid(args: Iterable<Py.CallArg>): Boolean = callArgsIssues(args) == null

fun callArgsIssues(args: Iterable<Py.CallArg>): String? {
    var kw = false // Can't have positional arguments after keyword
    var doubleStar = false
    for (arg in args) {
        if (kw && !arg.positional) {
            return "$arg: positional argument after keyword args"
        }
        if (doubleStar) {
            return "$arg: not allowed after any **argument"
        }
        kw = kw || !arg.positional
        doubleStar = doubleStar || arg.prefix == Py.ArgPrefix.DoubleStar
    }
    return null
}

fun argumentsValid(args: Iterable<Py.ArgLike>): Boolean = argumentsIssues(args) == null

fun argumentsIssues(args: Iterable<Py.ArgLike>): String? {
    var star = false // Only ** can follow *
    var doubleStar = false // Nothing can follow **
    var defaults = false // Can't have required arguments after optional
    args@ for (arg in args) {
        arg is Py.Arg || continue@args
        // Manually support this case in our transforms elsewhere.
        // if (defaults && arg.defaultValue == null && arg.prefix == Py.ArgPrefix.None) {
        //     return "$arg: required argument following optional"
        // }
        if (star && arg.prefix != Py.ArgPrefix.DoubleStar) {
            return "$arg: only **keywords can follow *vararg"
        }
        if (doubleStar) {
            return "$arg: nothing can follow **keywords"
        }
        star = star || arg.prefix == Py.ArgPrefix.Star
        doubleStar = doubleStar || arg.prefix == Py.ArgPrefix.DoubleStar
        defaults = defaults || arg.defaultValue != null
    }
    return null
}

/**
 * Let's us do: Call requires `callArgsValid(x), issue { callArgsIssues(x) }`
 */
fun issue(f: () -> String?): () -> String = {
    f() ?: "<no issue>"
}
