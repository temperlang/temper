package lang.temper.be

import lang.temper.library.DependencyResolver
import lang.temper.name.DashedIdentifier

interface MetadataDependencyResolver<BACKEND : Backend<BACKEND>> : DependencyResolver {
    val backend: Backend<BACKEND>

    fun <VALUE> getMetadata(libraryName: DashedIdentifier, key: MetadataKey<BACKEND, VALUE>): VALUE?
}

internal class MetadataDependencyResolverImpl<BACKEND : Backend<BACKEND>>(
    val dependencyResolver: DependencyResolver,
    override val backend: Backend<BACKEND>,
    private val getDependenciesBuilder: () -> Dependencies.Builder<BACKEND>?,
) : DependencyResolver by dependencyResolver, MetadataDependencyResolver<BACKEND> {
    override fun <VALUE> getMetadata(libraryName: DashedIdentifier, key: MetadataKey<BACKEND, VALUE>): VALUE? =
        getDependenciesBuilder()?.getMetadata(libraryName, key)
}
