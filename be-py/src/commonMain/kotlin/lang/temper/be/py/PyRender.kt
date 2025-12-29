package lang.temper.be.py

import lang.temper.format.CodeFormatter
import lang.temper.format.toStringViaTokenSink

fun formatAst(ast: Py.Tree): String = toStringViaTokenSink(formattingHints = PyFormattingHints, singleLine = false) {
    CodeFormatter(it).format(ast, true)
}
