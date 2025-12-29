package lang.temper.kcodegen.vscode.syntax

import kotlin.test.Test
import kotlin.test.assertEquals

class TemperLanguageTest {
    @Test
    fun availableEqualsUsed() {
        // All we need is the key set, but counting allows for easy manual inspection of something interesting.
        val refCounts = mutableMapOf<String, Int>()
        language.value.recurse().forEach { rule ->
            when (rule) {
                is Ref -> refCounts[rule.key] = refCounts.getOrElse(rule.key) { 0 } + 1
                else -> Unit
            }
        }
        assertEquals(language.value.repository.keys, refCounts.keys)
    }
}

fun Language.recurse() = sequence {
    patterns.forEach { yieldAll(it.recurse()) }
    repository.values.forEach { yieldAll(it.recurse()) }
}

fun Rule.recurse(): Sequence<Rule> = sequence {
    yield(this@recurse)
    when (this@recurse) {
        is Choice -> patterns.forEach { yieldAll(it.recurse()) }
        is Nest -> patterns?.forEach { yieldAll(it.recurse()) }
        is Flat, is Ref -> Unit
    }
}
