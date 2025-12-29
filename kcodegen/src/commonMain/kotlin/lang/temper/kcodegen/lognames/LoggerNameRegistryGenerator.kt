package lang.temper.kcodegen.lognames

import lang.temper.common.asciiTitleCase
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonString
import lang.temper.common.jsonEscaper
import lang.temper.common.putMultiSet
import lang.temper.common.structure.StructureParser
import lang.temper.common.toStringViaBuilder
import lang.temper.kcodegen.KotlinCodeGenerator
import lang.temper.kcodegen.globScanBestEffort
import lang.temper.lexer.isUnreservedIdentifier

internal object LoggerNameRegistryGenerator : KotlinCodeGenerator("log") {
    override val sourcePrefix: String
        get() = "$GENERATED_FILE_PREFIX(\"LoggerNameRegistryGenerator\")"
    private const val BASE_NAME = "Debug"

    override fun generateSources(): List<GeneratedKotlinSource> {
        val content = generateSourceContent()
        return listOf(
            GeneratedKotlinSource(
                packageNameParts = listOf(
                    "lang",
                    "temper",
                    "log",
                ),
                baseName = BASE_NAME,
                content = content,
            ),
        )
    }

    private fun generateSourceContent(): String {
        val files =
            globScanBestEffort(subProject, emptyList(), "**/lang/temper/log/logger-names.json")
                .toList()
        require(files.size == 1)
        val jsonContent = files[0].second()
        val json = StructureParser.parseJson(jsonContent, tolerant = true)
        // We have a list of strings that are each log names.
        val loggerNames = (json as JsonArray).map { (it as JsonString).s }.toSet()
        // We want to arrange them hierarchically by dots.
        // "foo.bar" has "foo" as a parent.
        val parentChildRels = mutableMapOf("*" to mutableSetOf<String>())
        for (loggerName in loggerNames) {
            val parts = loggerName.split(".")
            require(
                parts.isNotEmpty() &&
                    "" !in parts &&
                    parts.all { isUnreservedIdentifier(it) }, // Really we need Kotlin identifiers
            ) {
                loggerName
            }

            parentChildRels.getOrPut(loggerName) { mutableSetOf() }
            for (i in parts.lastIndex downTo 1) {
                val child = parts.subList(0, i + 1).joinToString(".")
                val parent = parts.subList(0, i).joinToString(".")
                parentChildRels.putMultiSet(parent, child)
            }
            parentChildRels.putMultiSet("*", parts[0])
        }
        val childToParent = mutableMapOf<String, String>()
        parentChildRels.forEach { (parent, children) ->
            children.forEach { childToParent[it] = parent }
        }

        return toStringViaBuilder { out ->
            out.append(sourcePrefix).append('\n')
            out.append("""@file:Suppress("ktlint")""").append("\n\n")
            out.append("package lang.temper.log\n")
            out.append('\n')
            out.append(
                """
                |/**
                | * Allow fine or coarse grained logging by fetching a
                | * [Console][lang.temper.common.Console] associated with a [CodeLocation] but
                | * filtered by location specific preferences around what to log via syntax like
                | *
                | *     Debug.Frontend.TypeStage(location).log(message)
                | *
                | * # Tiers
                | *
                | * Logging is configured by tiers.
                | * Tier 0 is broad component logging:
                | * - `frontend`
                | * - `backend`
                | * - `docgen`
                | *
                | * Tier 1 is more fine-grained, for example `frontend.typeStage`, and unless
                | * specifically configured the logging for `frontend.typeStage` falls back to that
                | * for `frontend`.
                | *
                | * Tier 2 is even more fine-grained, and has an extra layer of dots, etc.
                | *
                | * See `logger-names.json` which defines the logger names.
                | *
                | * Logger names may be introspected over via [logConfigurationsByName]
                | */
                |
                """.trimMargin(),
            )

            // We need to generate a mapping from "*.foo.bar" to `Debug.Foo.Bar` for logger
            // introspection
            val keyToKotlinName = mutableMapOf<String, String>()

            fun writeLogject(key: String, kotlinNames: List<String>) {
                keyToKotlinName[key] = kotlinNames.joinToString(".")
                val indent = "    ".repeat(kotlinNames.size - 1)
                val kotlinName = kotlinNames.last()

                val children = parentChildRels[key] ?: emptySet()

                val keyStr = jsonEscaper.escape(key)
                val parentKeyStr = childToParent[key]?.let { jsonEscaper.escape(it) } ?: "null"
                val childKeyStrs =
                    "listOf(${children.joinToString(", ") { jsonEscaper.escape(it) }})"

                out.append(indent)
                out.append(
                    "object $kotlinName : LogConfigurations($keyStr, $parentKeyStr, $childKeyStrs)",
                )
                if (children.isEmpty()) {
                    out.append("\n")
                } else {
                    out.append(" {\n")
                    children.forEach { childKey ->
                        val afterLastDot = childKey.substring(childKey.lastIndexOf('.') + 1)
                        writeLogject(childKey, kotlinNames + afterLastDot.asciiTitleCase())
                    }
                    out.append("$indent}\n")
                }
            }

            writeLogject("*", listOf(BASE_NAME))

            out.append("\n")
            out.append("val logConfigurationsByName: Map<String, LogConfigurations> = mapOf(\n")
            keyToKotlinName.forEach { (key, dottedKotlinName) ->
                out.append(
                    "    ${jsonEscaper.escape(key)} to $dottedKotlinName,\n",
                )
            }
            out.append(")\n")
        }
    }
}
