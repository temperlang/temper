package lang.temper.value

import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.type.SimpleHelpful
import java.util.WeakHashMap

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class HelpInfo(
    val longHelp: String,
    val briefHelp: String = "",
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class HelpSnippet(
    val briefHelp: String,
    val helpSnippet: String,
)

internal data class SnippetHelpInfo(
    val briefHelp: String,
    val helpSnippet: String,
)
private val helpSnippetMap = WeakHashMap<MacroValue, SnippetHelpInfo>()
fun helpSnippet(m: MacroValue, briefHelp: String, snippetId: String) {
    synchronized(helpSnippetMap) {
        helpSnippetMap[m] = SnippetHelpInfo(briefHelp, snippetId)
    }
}
internal fun helpDefined(m: MacroValue) = synchronized(helpSnippetMap) {
    helpSnippetMap[m]
}

fun HelpInfo.toHelpful(context: String): Helpful? {
    val longHelp = longHelp
    var briefHelp = briefHelp
    if (briefHelp.isEmpty()) {
        briefHelp = longHelp.lines().firstOrNull() ?: return null
    }
    return SimpleHelpful(
        briefHelp = briefHelp,
        longHelp = longHelp,
        context = context,
    )
}

fun HelpSnippet.toHelpful(context: String): Helpful? =
    SnippetHelpInfo(briefHelp = briefHelp, helpSnippet = helpSnippet).toHelpful(context)

internal fun SnippetHelpInfo.toHelpful(context: String): Helpful? {
    val key = helpSnippet
    val snippetText = HelpfulSnippets.getSnippetText(key) ?: return null

    return SimpleHelpful(
        briefHelp = briefHelp,
        longHelp = snippetText,
        context = context,
    )
}

const val HELPFUL_SNIPPETS_RESOURCE_PATH = "lang/temper/helpful/helpful-snippets.json"

private val snippets: Map<String, String> by lazy {
    val json = Helpful::class.java
        .getResourceAsStream("/$HELPFUL_SNIPPETS_RESOURCE_PATH")?.use { stream ->
            JsonValue.parse(stream.readAllBytes().toString(Charsets.UTF_8))
        }
    buildMap {
        for (p in (json?.result as? JsonObject)?.properties ?: emptyList()) {
            put(p.key, (p.value as JsonString).s)
        }
    }
}

object HelpfulSnippets {
    fun getSnippetText(key: String): String? = snippets[key]
    val topics get() = snippets.keys
}
