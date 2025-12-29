package lang.temper.be.csharp

import lang.temper.ast.OutTree
import lang.temper.be.Backend
import lang.temper.common.buildListMultimap
import lang.temper.common.putMultiList

class NameSimplifier(private val spec: Backend.TranslatedFileSpecification) {
    private val typeNames = mutableSetOf<CSharp.QualTypeName>()

    fun simplify() {
        findNames(spec.content)
        simplifyNames()
    }

    /** The identifiers should be free to add without copy. */
    private fun addUsings(usings: Map<List<CSharp.Identifier>, CSharp.Identifier>) {
        // Put usings outside any namespace so we don't conflict with other namespace members.
        // We'll only conflict with other namespaces, but since we're alias-qualifying all access to other namespaces,
        // we should be fine.
        val compilationUnit = spec.content as CSharp.CompilationUnit
        val pos = compilationUnit.pos
        compilationUnit.usings = usings.map { (ids, alias) ->
            CSharp.UsingNamespaceDirective(pos, alias = alias, ids = ids)
        }
    }

    private fun findNames(tree: OutTree<*>) {
        for (kid in tree.children) {
            when (kid) {
                is CSharp.QualTypeName -> if (kid.id.size > 1) {
                    typeNames.add(kid)
                }

                else -> findNames(kid)
            }
        }
    }

    private fun simplifyDotQualTypes(tree: OutTree<*>, aliases: Map<List<CSharp.Identifier>, CSharp.Identifier>) {
        for (kid in tree.children) {
            when (kid) {
                is CSharp.QualTypeName -> if (kid.id.size > 1) {
                    val alias = aliases.getValue(kid.id.subList(0, kid.id.size - 1))
                    kid.namespaceAlias = alias.deepCopy()
                    kid.id = listOf(kid.id.last())
                }

                else -> simplifyDotQualTypes(kid, aliases)
            }
        }
    }

    private fun simplifyNames() {
        val namespaces = typeNames.asSequence().map { it.id.subList(0, it.id.size - 1) }.toSet()
        // Sort before going further so we have predictable results. TODO Sort `System` before others?
        val divider = "\uFFFF" // Invalid utf16 but good enough for us.
        val sorted = namespaces.map { it.joinToString(divider) }.sorted().map { it.split(divider) }
        // For brevity, condense each namespace down to one letter.
        val aliases = buildListMultimap {
            for (namespace in sorted) {
                val name = namespace.last()
                val start = name.codePointAt(0)
                val startString = name.substring(0, Character.charCount(start))
                this.putMultiList(startString, namespace)
            }
        }
        val pos = spec.content.pos
        // But number them if we have multiple with the same name.
        val uniques = aliases.asSequence().flatMap { entry ->
            // Also reverse the key/value order so we can look up from qual names later.
            when (entry.value.size) {
                1 -> listOf(entry.value.first() to entry.key)
                else -> entry.value.mapIndexed { index, ids ->
                    ids to "${entry.key}$index"
                }
            }
        }.map { (ids, alias) -> ids.map { it.toIdentifier(pos) } to alias.toIdentifier(pos) }.toMap()
        addUsings(uniques)
        simplifyDotQualTypes(spec.content, uniques)
    }
}
