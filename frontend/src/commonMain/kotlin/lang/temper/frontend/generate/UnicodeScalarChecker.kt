package lang.temper.frontend.generate

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.C_MAX_SURROGATE
import lang.temper.common.C_MIN_SURROGATE
import lang.temper.common.Log
import lang.temper.common.MAX_HEX_IN_CP
import lang.temper.common.MIN_SUPPLEMENTAL_CP
import lang.temper.common.toHex
import lang.temper.frontend.Module
import lang.temper.lexer.decodeHex
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.regex.MAX_SUPPLEMENTAL_CP
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ErrorFn
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.functionContained
import lang.temper.value.valueContained

/** Give helpful messages for Unicode errors in calls to cat. */
class UnicodeScalarChecker(val module: Module) {
    fun check(root: BlockTree) {
        TreeVisit.startingAt(root).forEachContinuing tree@{ tree ->
            tree is CallTree || return@tree
            if (tree.childOrNull(0)?.functionContained == BuiltinFuns.strCatFn) {
                checkCodePoints(tree)
            }
        }.visitPreOrder()
    }

    private fun checkCodePoints(tree: Tree) {
        // Examples of things we're looking for:
        // - (Call
        //     (V error)
        //     (Call (V list) (V "\\u{110000}")))
        // - (Call
        //     (V error)
        //     (Call (V list) (V "\\ud800")))
        //   (Call
        //     (V error)
        //     (Call (V list) (V "\\udc00")))
        // - (Call
        //     (V error)
        //     (Call (V list) (V "\\u{d800}")))
        //   (V "")
        //   (Call
        //     (V error)
        //     (Call (V list) (V "\\u{dc00}")))
        var previous = 0.toChar()
        var previousPos = null as Position?
        fun flushAnyPrevious() {
            if (previousPos != null) {
                logError(MessageTemplate.InvalidUnicodeBecauseSurrogate, previousPos!!)
                previousPos = null
            }
        }
        @Suppress("LoopWithTooManyJumpStatements") // Enables flatter code.
        kids@ for (index in 1..<tree.size) {
            val kid = tree.child(index)
            when (kid) {
                is CallTree -> kid.childOrNull(0)?.functionContained == ErrorFn || continue@kids
                is ValueLeaf -> {
                    val text = TString.unpackOrNull(kid.valueContained) ?: continue@kids
                    if (text.isNotEmpty()) {
                        flushAnyPrevious()
                    }
                    continue@kids
                }
                else -> continue@kids
            }
            val error = kid.childOrNull(1) as? CallTree ?: continue@kids
            error.size == 2 || continue@kids
            error.child(0).functionContained == BuiltinFuns.listifyFn || continue@kids
            val raw = TString.unpackOrNull(error.child(1).valueContained) ?: continue@kids
            // At this point, error nodes with Unicode escapes are single and either without without braces.
            raw.startsWith(U) || continue@kids
            // Find the attempted code point, including decoding any attempted surrogate pairs.
            val decoded = when {
                raw.startsWith(U) -> when (raw[U.length]) {
                    '{' -> decodeHex(raw, UBRACE.length, raw.length - 1)
                    else -> decodeHex(raw, 2, raw.length)
                }
                else -> null
            }
            if (decoded == null) {
                logError(MessageTemplate.InvalidUnicode, error.pos)
                continue@kids
            }
            var pos = error.pos
            val codePoint = when {
                decoded < MIN_SUPPLEMENTAL_CP -> {
                    val char = decoded.toChar()
                    when {
                        previousPos != null && Character.isSurrogatePair(previous, char) -> {
                            val codePoint = Character.toCodePoint(previous, char)
                            pos = previousPos!!.copy(right = pos.right)
                            previousPos = null
                            codePoint
                        }
                        else -> {
                            if (Character.isHighSurrogate(char)) {
                                flushAnyPrevious()
                                previous = char
                                previousPos = error.pos
                                continue@kids
                            }
                            decoded
                        }
                    }
                }
                else -> {
                    flushAnyPrevious()
                    // Negative if either too many digits or int overflow value.
                    when {
                        raw.length - (UBRACE.length + 1) > MAX_HEX_IN_CP -> -1
                        else -> decoded
                    }
                }
            }
            // Our error is made of valid hex digits, so figure out what error case it must be.
            when (codePoint) {
                in C_MIN_SURROGATE..C_MAX_SURROGATE -> {
                    logError(MessageTemplate.InvalidUnicodeBecauseSurrogate, pos)
                }
                !in 0..MAX_SUPPLEMENTAL_CP -> {
                    logError(MessageTemplate.InvalidUnicodeBecauseLarge, pos)
                }
                else -> {
                    // Valid scalar value, so it must have been made from a surrogate pair.
                    check(codePoint >= MIN_SUPPLEMENTAL_CP)
                    logError(MessageTemplate.InvalidUnicodeBecauseSurrogatePair, pos, listOf(codePoint.toHex()))
                }
            }
        }
        flushAnyPrevious()
    }

    private fun logError(template: MessageTemplateI, pos: Position, values: List<Any> = listOf()) {
        module.logSink.log(Log.Error, template, pos, values)
    }
}

private const val U = "\\u"
private const val UBRACE = "$U{"
