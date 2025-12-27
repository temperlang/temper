package lang.temper.be.java

import lang.temper.be.MetadataKey
import lang.temper.log.FilePath
import lang.temper.name.BackendId

abstract class JavaMetadataKey<VALUE_TYPE>(
    lang: JavaLang,
    private val name: String,
) : MetadataKey<JavaBackend, VALUE_TYPE>() {
    override val backendId: BackendId = lang.backendId

    override fun equals(other: Any?): Boolean =
        other is JavaMetadataKey<*> && name == other.name && backendId == other.backendId

    override fun hashCode(): Int =
        name.hashCode() + 31 * backendId.hashCode()

    override fun toString(): String = name

    /** Name of top-level main class for `temper run` */
    class MainClass(lang: JavaLang) : JavaMetadataKey<QualifiedName>(lang, "MainClass")

    /** Names of main classes for `temper run` */
    class MainClasses(lang: JavaLang) : JavaMetadataKey<List<QualifiedName>>(lang, "MainClasses")

    /** The group:artifact:version the library. */
    class LibraryArtifact(lang: JavaLang) : JavaMetadataKey<Artifact>(lang, "LibraryArtifact")

    /** Relates */
    class ArtifactToSourceDirForDependencies(
        lang: JavaLang,
    ) : JavaMetadataKey<Map<Artifact, Java.SourceDirectory>>(lang, "ArtifactToSourceDirForDependencies")

    class PomFilePath(lang: JavaLang) : JavaMetadataKey<FilePath>(lang, "PomFilePath")

    /** The set of package declarations from translated Java source files */
    class Packages(lang: JavaLang) : JavaMetadataKey<PackageLists>(lang, "Packages")
}
