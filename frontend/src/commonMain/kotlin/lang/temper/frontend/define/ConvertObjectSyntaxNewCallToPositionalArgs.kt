package lang.temper.frontend.define

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.interp.EmptyEnvironment
import lang.temper.interp.LazyActualsList
import lang.temper.interp.isKnownStable
import lang.temper.type.MethodKind
import lang.temper.type.NominalType
import lang.temper.type.TypeShape
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.TNull
import lang.temper.value.firstArgumentIndex
import lang.temper.value.freeTree
import lang.temper.value.isNewCall
import lang.temper.value.parameterNameSymbols
import lang.temper.value.staticTypeContained
import kotlin.math.max
import kotlin.math.min

internal fun convertObjectSyntax(root: BlockTree) {
    val newCalls = buildList {
        TreeVisit.startingAt(root)
            .forEachContinuing {
                if (it is CallTree && isNewCall(it)) {
                    add(it)
                }
            }
            .visitPreOrder()
    }
    for (newCall in newCalls) {
        convertObjectSyntaxNewCallToPositionalArgs(newCall)
    }
}

internal fun convertObjectSyntaxNewCallToPositionalArgs(newCall: CallTree) {
    val constructedType = newCall.childOrNull(1)?.staticTypeContained as? NominalType ?: return
    val typeShape = constructedType.definition as? TypeShape ?: return
    val argList = newCall.children.subListToEnd(newCall.firstArgumentIndex)
    if (argList.isEmpty()) {
        return
    }

    val namedArgList = LazyActualsList(argList, null, EmptyEnvironment, InterpMode.Partial)
    val symbols = buildList {
        for (i in namedArgList.indices) {
            val key = namedArgList.key(i)
                ?: return // Not a property bag
            add(key)
        }
    }

    // filter constructors in typeShape by the symbols used
    val constructors = typeShape.methods.filter { it.methodKind == MethodKind.Constructor }

    constructorLoop@
    for (possibleConstructor in constructors) {
        val parameterNameSymbols = possibleConstructor.parameterNameSymbols ?: continue
        val remapping = mutableListOf<Int?>()
        var nRequiredMatched = 0
        for ((symbolIndex, symbol) in symbols.withIndex()) {
            val reqIndex = parameterNameSymbols.requiredSymbols.indexOf(symbol)
            val position: Int
            if (reqIndex >= 0) {
                nRequiredMatched++
                position = reqIndex
            } else {
                val optIndex = parameterNameSymbols.optionalSymbols.indexOf(symbol)
                if (optIndex < 0) {
                    continue@constructorLoop
                }
                position = optIndex + parameterNameSymbols.requiredSymbols.size
            }
            while (remapping.size <= position) {
                remapping.add(null)
            }
            if (remapping[position] != null) {
                return
            }
            remapping[position] = symbolIndex
        }
        if (nRequiredMatched != parameterNameSymbols.requiredSymbols.size) {
            continue@constructorLoop
        }
        // We have a valid remapping.

        // We might need to capture some arguments in temporaries to preserve order of operations.
        var firstComplexArg = Int.MAX_VALUE
        var lastComplexArg = -1
        for (i in namedArgList.indices) {
            val argTree = namedArgList.valueTree(i)
            if (!isKnownStable(argTree)) {
                firstComplexArg = min(i, firstComplexArg)
                lastComplexArg = max(i, lastComplexArg)
            }
        }
        // But if those arguments are already in-order, we don't need to after all.
        if (firstComplexArg < lastComplexArg) {
            var lastInComplexRange = -1
            var inOrder = true
            for (i in remapping) {
                if (i != null && i in firstComplexArg..lastComplexArg) {
                    if (lastInComplexRange < i) {
                        lastInComplexRange = i
                    } else {
                        inOrder = false
                        break
                    }
                }
            }
            if (inOrder) {
                lastComplexArg = -1
            }
        }

        val temporaries = if (lastComplexArg >= 0) {
            val nameMaker = newCall.document.nameMaker
            buildList {
                for (i in namedArgList.indices) {
                    if (i in firstComplexArg..lastComplexArg) {
                        add(nameMaker.unusedTemporaryName(namedArgList.key(i)!!.text))
                    } else {
                        add(null)
                    }
                }
            }
        } else {
            listOf()
        }

        val valueTrees = namedArgList.indices.map { namedArgList.valueTree(it) }
        // Replant the arguments without any key symbols, and with `null`s for optionals.
        newCall.replace(newCall.firstArgumentIndex until newCall.size) {
            for (actualIndex in remapping) {
                if (actualIndex != null) {
                    val pos = namedArgList.pos(actualIndex)
                    val temporary = temporaries.getOrNull(actualIndex)
                    if (temporary != null) {
                        Rn(pos, temporary)
                    } else {
                        Replant(freeTree(valueTrees[actualIndex]))
                    }
                } else {
                    V(TNull.value)
                }
            }
        }

        // If we needed to capture some args in temporaries to preserve OoO, wrap
        // the call in a block that introduces and initializes those temporaries.
        if (temporaries.isNotEmpty()) {
            val callEdge = newCall.incoming!!
            callEdge.replace {
                Block {
                    for (i in temporaries.indices) {
                        val temporary = temporaries[i] ?: continue
                        val initializer = valueTrees[i]
                        val initializerLeft = initializer.pos.leftEdge
                        Decl(initializerLeft, temporary)
                        Call(initializerLeft, BuiltinFuns.setLocalFn) {
                            Ln(initializerLeft, temporary)
                            Replant(freeTree(initializer))
                        }
                    }
                    Replant(freeTree(newCall))
                }
            }
        }

        return
    }
}
