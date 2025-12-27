package lang.temper.common

import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue

/**
 * [invalidUnicodeString]`( """ "foo\uD800" """ )` will resolve to the string value `"foo\uD800"`
 * but does not rely on kotlinc to properly encode string literals with orphaned surrogates or
 * other invalid code-points.
 *
 * Prefer it to Kotlin string literals when testing String corner cases, especially for tests that
 * run via the Kotlin/JS backend.
 */
fun invalidUnicodeString(
    jsonTextOfString: String,
): String = (JsonValue.parse(jsonTextOfString).result as JsonString).s
