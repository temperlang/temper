package lang.temper.docgen.transformations

import lang.temper.name.LanguageLabel

fun wrapForLangSpecifics(input: String, lang: LanguageLabel) = "<span class=\"wrapper-$lang\">$input</span>"

/**
 * Produces a style so that classes beginning with [prefix]
 */
fun oneLangOnlyStyle(prefix: String): String {
    return """<style>
    a[class*=' $prefix-'], a[class^='link-']{display: none}
    a[class*=' $prefix-']:first-of-type, a[class^='link-']:first-of-type { display: block }
</style>
    """
}
