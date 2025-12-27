package lang.temper.common

import lang.temper.lexer.Genre
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.ConfigurationKey
import lang.temper.log.SharedLocationContext
import lang.temper.name.ModuleLocation
import lang.temper.name.NamingContext
import lang.temper.value.DependencyCategory
import lang.temper.value.DocumentContext

class TestDocumentContext(
    override val loc: ModuleLocation = testModuleName,
    override val sharedLocationContext: SharedLocationContext = TestSharedLocationContext,
    override val genre: Genre = Genre.Library,
) : NamingContext(), DocumentContext, ConfigurationKey {
    override val definitionMutationCounter = AtomicCounter()
    override val namingContext: NamingContext get() = this
    override val dependencyCategory = DependencyCategory.Production
    override val configurationKey: ConfigurationKey get() = this
}

private data object TestSharedLocationContext : SharedLocationContext {
    override fun <T : Any> get(loc: CodeLocation, v: CodeLocationKey<T>): T? = null
}
