package lang.temper.common.dtree

import lang.temper.common.assertStringsEqual
import lang.temper.common.temperEscaper
import kotlin.test.Test

// For string picking, perfect hashing is best, but applying discrimination
// based on char lets us test properties of DTree building.

class DecisionTreeTest {
    @Suppress("SpellCheckingInspection") // IDEA, why do you hate long words?
    @Test
    fun someLongWordsSwitchingOnExactChars() = assertStringDecisionTree(
        listOf(
            "anthropomorphologically",
            "blepharosphincterectomy",
            "epididymodeferentectomy",
            "formaldehydesulphoxylic",
            "gastroenteroanastomosis",
            "hematospectrophotometer",
            "macracanthrorhynchiasis",
            "pancreaticoduodenostomy",
            "pathologicohistological",
            "pericardiomediastinitis",
            "phenolsulphonephthalein",
            "philosophicotheological",
            "pseudolamellibranchiate",
            "scientificogeographical",
            "thymolsulphonephthalein",
            "transubstantiationalist",
        ),
        CharCompareKind.CharSwitch,
        """
            |when (case[3]) {
            |  'h' ->
            |    when (case[0]) {
            |      'a' -> "anthropomorphologically"
            |      'p' -> "pathologicohistological"
            |    }
            |  'p' -> "blepharosphincterectomy"
            |  'd' -> "epididymodeferentectomy"
            |  'm' ->
            |    when (case[0]) {
            |      'f' -> "formaldehydesulphoxylic"
            |      't' -> "thymolsulphonephthalein"
            |    }
            |  't' -> "gastroenteroanastomosis"
            |  'a' -> "hematospectrophotometer"
            |  'r' -> "macracanthrorhynchiasis"
            |  'c' -> "pancreaticoduodenostomy"
            |  'i' -> "pericardiomediastinitis"
            |  'n' ->
            |    when (case[0]) {
            |      'p' -> "phenolsulphonephthalein"
            |      't' -> "transubstantiationalist"
            |    }
            |  'l' -> "philosophicotheological"
            |  'u' -> "pseudolamellibranchiate"
            |  'e' -> "scientificogeographical"
            |}
        """.trimMargin(),
    )

    @Test
    fun sometimesBinarySearchIsTheBest() = assertStringDecisionTree(
        // Here we know a priori what the best strategy is.
        listOf("a", "b", "c", "d", "e", "f", "g", "h"),
        CharCompareKind.BinaryComparison,
        """
            |if (case[0] <= 'd') {
            |  if (case[0] <= 'b') {
            |    if (case[0] <= 'a') {
            |      "a"
            |    } else {
            |      "b"
            |    }
            |  } else {
            |    if (case[0] <= 'c') {
            |      "c"
            |    } else {
            |      "d"
            |    }
            |  }
            |} else {
            |  if (case[0] <= 'f') {
            |    if (case[0] <= 'e') {
            |      "e"
            |    } else {
            |      "f"
            |    }
            |  } else {
            |    if (case[0] <= 'g') {
            |      "g"
            |    } else {
            |      "h"
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun someShortStrings() = assertStringDecisionTree(
        listOf(
            "one",
            "two",
            "three",
            "four",
            "five",
        ),
        CharCompareKind.BinaryComparison,
        // A 3/2 split is a decent first choice.
        """
            |if (case[0] <= 'o') {
            |  if (case[0] > 'f') {
            |    "one"
            |  } else {
            |    if (case[1] > 'n') {
            |      "four"
            |    } else {
            |      "five"
            |    }
            |  }
            |} else {
            |  if (case[1] > 'n') {
            |    "two"
            |  } else {
            |    "three"
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun cannotDiscriminateLeadsToGracefulExit() = assertStringDecisionTree(
        listOf(
            "feed",
            "food", // I'm not unique
            "good",
            "gold",
            "food", // Cannot be discriminated from the earlier food
            "glad",
        ),
        CharCompareKind.BinaryComparison,
        // A 3/3 split followed by nested 2/1 splits seems pretty good
        """
            |if (case[0] <= 'f') {
            |  if (case[1] <= 'e') {
            |    "feed"
            |  } else {
            |    "food", "food"
            |  }
            |} else {
            |  if (case[1] > 'l') {
            |    if (case[2] > 'l') {
            |      "good"
            |    } else {
            |      "gold"
            |    }
            |  } else {
            |    "glad"
            |  }
            |}
        """.trimMargin(),
    )
}

private enum class CharCompareKind {
    BinaryComparison,
    CharSwitch,
}

private fun assertStringDecisionTree(
    cases: List<String>,
    /** False to do a single binary comparison (<= or >=) a value or true to match an exact char. */
    kind: CharCompareKind,
    want: String,
) {
    val dtree = buildStringDecisionTree(cases, kind)
    val got = buildString {
        fun write(t: DecisionTree<String, CharDiscriminant>, indentLevel: Int) {
            when (t) {
                is DecisionTree.Inner -> {
                    val lastChoiceIndex = t.choices.size - 1
                    t.choices.entries.toList().forEachIndexed { choiceIndex, choiceEntry ->
                        t.discriminant.describeChoice(
                            isFirst = choiceIndex == 0,
                            isLast = choiceIndex == lastChoiceIndex,
                            category = choiceEntry.key,
                            indentLevel = indentLevel,
                            out = this,
                            choiceIsLeaf = choiceEntry.value is DecisionTree.Leaf,
                            renderChoice = { indentLevelForChoice ->
                                write(choiceEntry.value, indentLevelForChoice)
                            },
                        )
                    }
                }
                is DecisionTree.Leaf -> {
                    indentTo(indentLevel, this)
                    if (t.cases.isEmpty()) {
                        append("EMPTY")
                    } else {
                        t.cases.joinTo(this) {
                            temperEscaper.escape(it)
                        }
                    }
                }
            }
            append('\n')
        }
        write(dtree, 0)
    }
    assertStringsEqual(
        want.trimEnd(),
        got.trimEnd(),
    )
}

private fun buildStringDecisionTree(
    cases: List<String>,
    kind: CharCompareKind,
): DecisionTree<String, CharDiscriminant> {
    val maxLength = if (cases.isEmpty()) { 0 } else cases.maxOf { it.length }
    val discriminants = buildList {
        for (charIndex in 0 until maxLength) {
            when (kind) {
                CharCompareKind.BinaryComparison -> {
                    val uniqueValues = mutableSetOf<Int>()
                    for (case in cases) {
                        uniqueValues.add(case.getOrNull(charIndex)?.code ?: -1)
                    }
                    for (uniqueValue in uniqueValues) {
                        add(CharCompareDiscriminant(charIndex, uniqueValue))
                    }
                }
                CharCompareKind.CharSwitch -> {
                    add(CharAtDiscriminant(charIndex))
                }
            }
        }
    }
    return buildDecisionTree(cases, discriminants.toList()) { case, discriminant ->
        discriminant.categorize(case)
    }
}

private sealed class CharDiscriminant(
    val charIndex: Int,
) {
    abstract fun categorize(s: String): List<Any>

    abstract fun describeChoice(
        isFirst: Boolean,
        isLast: Boolean,
        category: Any,
        indentLevel: Int,
        out: StringBuilder,
        choiceIsLeaf: Boolean,
        renderChoice: (Int) -> Unit,
    )
}

private class CharAtDiscriminant(
    charIndex: Int,
) : CharDiscriminant(charIndex) {
    override fun categorize(s: String): List<Any> =
        listOf(s.getOrNull(charIndex)?.code ?: -1)

    override fun describeChoice(
        isFirst: Boolean,
        isLast: Boolean,
        category: Any,
        indentLevel: Int,
        out: StringBuilder,
        choiceIsLeaf: Boolean,
        renderChoice: (Int) -> Unit,
    ) {
        require(category is Int)
        if (isFirst) {
            indentTo(indentLevel, out)
            out.append("when (case[$charIndex]) {\n")
        }
        indentTo(indentLevel + 1, out)
        if (category == -1) {
            out.append("null")
        } else {
            out.append(escChar(category.toChar()))
        }
        out.append(" ->")
        if (choiceIsLeaf) {
            out.append(' ')
            renderChoice(0)
        } else {
            out.append('\n')
            renderChoice(indentLevel + 2)
        }
        if (isLast) {
            indentTo(indentLevel, out)
            out.append("}")
        }
    }
}

private class CharCompareDiscriminant(
    charIndex: Int,
    val pivot: Int,
) : CharDiscriminant(charIndex) {
    override fun categorize(s: String): List<Any> {
        val c = s.getOrNull(charIndex)?.code ?: -1
        return listOf(c <= pivot)
    }

    override fun describeChoice(
        isFirst: Boolean,
        isLast: Boolean,
        category: Any,
        indentLevel: Int,
        out: StringBuilder,
        choiceIsLeaf: Boolean,
        renderChoice: (Int) -> Unit,
    ) {
        require(category is Boolean)
        indentTo(indentLevel, out)
        if (!isFirst) {
            out.append("} else ")
        }
        if (!isLast) {
            out.append("if (")
            out.append(toString(category))
            out.append(") ")
        }
        out.append("{\n")
        renderChoice(indentLevel + 1)
        if (isLast) {
            indentTo(indentLevel, out)
            out.append("}")
        }
    }

    override fun toString(): String = toString(true)
    fun toString(isLessThanPivot: Boolean): String = when {
        pivot == -1 ->
            if (isLessThanPivot) {
                "$charIndex !in case"
            } else {
                "$charIndex in case"
            }
        else -> "case[$charIndex] ${if (isLessThanPivot) "<=" else ">"} ${escChar(pivot.toChar())}"
    }
}

fun escChar(ch: Char): String = when (ch) {
    '\\' -> "'\\\\'"
    '\t' -> "'\\t'"
    '\n' -> "'\\n'"
    '\r' -> "'\\r'"
    '\'' -> "'\\''"
    in '\u0000'..'\u000F' -> "'\\x0${ch.code.toString(16)}'"
    in '\u0010'..'\u001F' -> "'\\x${ch.code.toString(16)}'"
    else -> "'$ch'"
}

private fun indentTo(indentLevel: Int, out: StringBuilder) {
    repeat(indentLevel) { out.append("  ") }
}
