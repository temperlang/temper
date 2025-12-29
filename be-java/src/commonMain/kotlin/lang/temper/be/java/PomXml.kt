package lang.temper.be.java

import lang.temper.be.Dependencies
import lang.temper.library.LibraryConfiguration
import lang.temper.library.authors
import lang.temper.library.description
import lang.temper.library.homepage
import lang.temper.library.license
import lang.temper.library.repository
import org.redundent.kotlin.xml.Namespace
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml

private val xsiNs = Namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")

// Putting these together; they are defined in the parent Project Object Model:
// https://github.com/apache/maven/blob/maven-3.2.5/maven-model-builder/src/main/resources/org/apache/maven/model/pom-4.0.0.xml#L53
const val BASE_DIR = $$"${project.basedir}"
const val TEMPER_DIR = "$BASE_DIR/src/main/temper"
const val OUTPUT_CLASSES_PATH = $$"${project.build.outputDirectory}"

private val compilerPlugin = Artifact("org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0")
private val jarPlugin = Artifact("org.apache.maven.plugins", "maven-jar-plugin", "3.3.0")
private val execPlugin = Artifact("org.codehaus.mojo", "exec-maven-plugin", "3.1.0")
private val enforcerPlugin = Artifact("org.apache.maven.plugins", "maven-enforcer-plugin", "3.2.1")
private val gpgPlugin = Artifact("org.apache.maven.plugins", "maven-gpg-plugin", "3.1.0")
private val javadocPlugin = Artifact("org.apache.maven.plugins", "maven-javadoc-plugin", "3.5.0")
private val sourcePlugin = Artifact("org.apache.maven.plugins", "maven-source-plugin", "3.3.0")
private val publishingPlugin = Artifact("org.sonatype.central", "central-publishing-maven-plugin", "0.8.0")
private val surefirePlugin = Artifact("org.apache.maven.plugins", "maven-surefire-plugin", "3.0.0")
private val toolchainPlugin = Artifact("org.apache.maven.plugins", "maven-toolchains-plugin", "3.1.0")
internal val junitDependency = Dependency(
    Java.SourceDirectory.TestJava,
    Artifact("org.junit.jupiter", "junit-jupiter", "5.9.2"),
)
internal val temperCoreDependency = loadTemperCoreDependency()

internal expect fun loadTemperCoreDependency(): Dependency

internal data class Dependency(
    val sourceDir: Java.SourceDirectory,
    val artifact: Artifact,
) : Comparable<Dependency> {
    override fun compareTo(other: Dependency) = compareValuesBy(this, other, { it.sourceDir }, { it.artifact })
}

data class PomXml(
    val projectArtifact: Artifact,
    private val node: Node,
) {
    override fun toString(): String = node.toString(printOptions)
}

internal fun pomXml(
    projectArtifact: Artifact,
    config: LibraryConfiguration,
    javaVersion: String,
    javaMainClasses: List<QualifiedName> = listOf(),
    testClasses: List<QualifiedName> = listOf(),
    includeTemperSources: Boolean = false,
    selectJdk: SelectJdk = SelectJdk.IgnoreJdk,
    dependencies: List<Dependency> = listOf(),
    sourceEncoding: String = "UTF-8",
    outputEncoding: String = "UTF-8",
) = PomXml(
    projectArtifact,
    xml("project") {
        xmlns = "http://maven.apache.org/POM/4.0.0"

        // helps IDEs validate the XML.
        // schemaLocation is a list of pairs of namespace to schema-file
        // yes, the .xsd itself declares exactly which namespace it applies to.
        attribute(
            "schemaLocation",
            "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd",
            xsiNs,
        )

        // Wouldn't be XML if we didn't specify the version number in quadruplicate.
        "modelVersion" { -"4.0.0" }
        projectArtifact.place(this)

        // Metadata required by Sonatype.
        "name" { -config.libraryName.text }
        // Default the description because it's required but doesn't have to be unique from the name.
        "description" { -(config.description() ?: config.libraryName.text) }
        // These others are harder to guarantee, but still default where reasonable.
        config.authors()?.let { "developers" { "developer" { "name" { -it } } } }
        (config.homepage() ?: config.repository())?.let { "url" { -it } }
        config.license()?.let { "licenses" { "license" { "name" { -it } } } }
        config.repository()?.let { "scm" { "url" { -it } } }

        "properties" {
            "project.build.sourceEncoding" { -sourceEncoding }
            "project.reporting.outputEncoding" { -outputEncoding }
        }
        if (dependencies.isNotEmpty()) {
            "dependencies" {
                for ((sourceDir, dependency) in dependencies.sorted()) {
                    "dependency" {
                        dependency.place(this)
                        "type" { -"jar" }
                        "scope" { -sourceDir.mavenScope }
                    }
                }
            }
        }

        "build" {
            if (includeTemperSources) {
                "resources" {
                    comment("Add Temper sources as resources; these are manipulated later.")
                    "directory" { -TEMPER_DIR }
                }
            }
            "plugins" {
                addDeploymentPlugins()
                "plugin" {
                    compilerPlugin.place(this)
                    "configuration" {
                        when (javaVersion) {
                            Java8Specifics.majorVersionText -> {
                                "source" { -javaVersion }
                                "target" { -javaVersion }
                            }
                            else -> {
                                // Not available until Java 9, and people building to Java 8 might just hava Java 8.
                                // So only use this for newer versions, but it's supposed to check safe API usage in
                                // addition to controlling source and target.
                                // See: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html#option-release
                                "release" { -javaVersion }
                            }
                        }
                    }
                }
                selectJdk.plugin(this)
                "plugin" {
                    comment("https://maven.apache.org/plugins/maven-jar-plugin/")
                    jarPlugin.place(this)
                    "configuration" {
                        if (javaMainClasses.size == 1) {
                            "archive" {
                                comment("https://maven.apache.org/shared/maven-archiver/index.html details contents")
                                "manifest" {
                                    "mainClass" { -javaMainClasses[0].fullyQualified }
                                }
                            }
                        }
                        if (includeTemperSources) {
                            comment("Exclude Temper sources from the runtime package.")
                            "excludes" {
                                "exclude" { -"$TEMPER_DIR/**" }
                            }
                        }
                    }
                }
                if (javaMainClasses.isNotEmpty()) {
                    "plugin" {
                        comment("https://www.mojohaus.org/exec-maven-plugin/")
                        execPlugin.place(this)
                        "executions" {
                            for (javaMainClass in javaMainClasses.sorted()) {
                                "execution" {
                                    val main = javaMainClass.fullyQualified
                                    "id" { -main }
                                    "goals" {
                                        comment("Run in the Maven VM via `mvn compile exec:java@$main`")
                                        "goal" { -"java" }
                                        comment("Run in a forked JVM via `mvn compile exec:exec@$main`")
                                        "goal" { -"exec" }
                                    }
                                    "configuration" {
                                        comment("Used by exec:java")
                                        "mainClass" { -main }
                                        comment("Used by exec:exec")
                                        "executable" { -"java" }
                                        "arguments" {
                                            "argument" { -"-classpath" }
                                            "argument" { -OUTPUT_CLASSES_PATH }
                                            "argument" { -main }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (testClasses.isNotEmpty()) {
                    "plugin" {
                        surefirePlugin.place(this)
                        "configuration" {
                            comment(
                                "Configuration is mostly documented in the source: " +
                                    "https://github.com/apache/maven-surefire/blob/surefire-3.0.0/" +
                                    "maven-surefire-plugin/src/main/java/org/apache/maven/plugin/surefire/" +
                                    "SurefirePlugin.java",
                            )
                            "reportFormat" { -"xml" }
                            "includes" {
                                for (testClass in testClasses) {
                                    "include" { -testClass.fullyQualified }
                                }
                            }
                        }
                    }
                }
                if (includeTemperSources) {
                    // TODO How does this align with source jar requirements for OSSRH?
                    // TODO Should we include both Temper and Java?
                    // TODO Can a plugin be listed twice, or do we need to combine the config?
                    "plugin" {
                        comment("https://maven.apache.org/plugins/maven-source-plugin/")
                        sourcePlugin.place(this)
                        "configuration" {
                            "includes" {
                                comment("Include Temper sources in the source package.")
                                "include" { -"$TEMPER_DIR/**" }
                            }
                            "attach" { -"true" }
                        }
                    }
                }
            }
        }
    },
)

internal fun groupingPom(
    lang: JavaLang,
    dependencies: Dependencies<JavaBackend>,
): PomXml = PomXml(
    groupingPomArtifact,
    xml("project") {
        xmlns = "http://maven.apache.org/POM/4.0.0"
        attribute(
            "schemaLocation",
            "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd",
            xsiNs,
        )
        "modelVersion" { -"4.0.0" }
        groupingPomArtifact.place(this)
        "packaging" {
            -"pom"
        }

        "description" {
            -"""
                |Groups modules so that Temper tooling can use mvn at the command line to run
                |in the context of multiple, separately-compiled Java libraries
            """.trimMargin()
        }

        "modules" {
            "module" {
                -"temper-core" // It's good to have the Java support code on the classpath.
            }
            // Having a module for each library directory prompts mvn to look into those
            // directories for the POMs generated by pomXml() above and to build them in
            // an order that makes sense based on their internal dependencies.
            dependencies.libraryConfigurations.byLibraryName.keys.forEach { libraryName ->
                "module" {
                    -libraryName.text
                }
            }
        }

        // Including the dependencies mean we get the prod classpath for each compiled library.
        "dependencies" {
            dependencies.libraryConfigurations.byLibraryName.keys.forEach { libraryName ->
                val artifact = dependencies.metadata[libraryName, JavaMetadataKey.LibraryArtifact(lang)]
                if (artifact != null) {
                    "dependency" {
                        artifact.place(this)
                    }
                }
            }
        }
    },
)

fun Node.addDeploymentPlugins() {
    // For now, always set up publishing configurataion for Maven Central.
    // TODO Also allow some customization around publishing configuration.
    "plugin" {
        // Depends on user settings for authentication.
        // See: https://central.sonatype.org/publish/publish-portal-maven/
        publishingPlugin.place(this)
        "extensions" { -"true" }
        "configuration" {
            "publishingServerId" { -"central" }
            // Other backends don't bother with separate staging, so just autopublish here, too.
            "autoPublish" { -"true" }
        }
    }
    "plugin" {
        sourcePlugin.place(this)
        "executions" {
            "execution" {
                "id" { -"attach-sources" }
                "goals" {
                    "goal" { -"jar-no-fork" }
                }
            }
        }
    }
    "plugin" {
        javadocPlugin.place(this)
        "configuration" {
            "failOnError" { -"false" }
            // Do not generate warnings for every API element without a javadoc comment
            "doclint" { -"-missing" }
            "docencoding" { -"UTF-8" }
        }
        "executions" {
            "execution" {
                "id" { -"attach-javadocs" }
                "goals" {
                    "goal" { -"jar" }
                }
            }
        }
    }
    "plugin" {
        // Depends on user settings for configuration and passphrase.
        // See: https://central.sonatype.org/publish/publish-maven/#gpg-signed-components
        gpgPlugin.place(this)
        "executions" {
            "execution" {
                "id" { -"sign-artifacts" }
                "phase" { -"verify" }
                "goals" {
                    "goal" { -"sign" }
                }
                "configuration" {
                    // Yes, the same value for both tags, and yes, the dollar is escaped for raw text.
                    // TODO: should these be "keyname" and "passphraseServerId" instead of "id"?
                    "id" { -"\${gpg.keyname}" }
                    "id" { -"\${gpg.keyname}" }
                }
            }
        }
    }
}

private val printOptions = PrintOptions(pretty = true, indent = "  ", singleLineTextElements = true)

/** These tags are used all over Maven. */
data class Artifact(val groupId: String, val artifactId: String, val version: String) : Comparable<Artifact> {
    fun place(node: Node) = node.apply {
        "groupId" { -this@Artifact.groupId }
        "artifactId" { -this@Artifact.artifactId }
        "version" { -this@Artifact.version }
    }

    fun toMavenString() = "$groupId:$artifactId:$version"

    override fun compareTo(other: Artifact) =
        // TODO Better version comparison.
        compareValuesBy(this, other, { it.groupId }, { it.artifactId }, { it.version })
}

/** Artifact for a POM that groups libraries. */
val groupingPomArtifact = Artifact("not.for.publication", "grouping-pom", "1.0-SNAPSHOT")

sealed interface SelectJdk {
    fun plugin(node: Node)

    /** Hope for the best and don't try to select a JDK. Fine for Docker environments. */
    data object IgnoreJdk : SelectJdk {
        override fun plugin(node: Node) {}
    }

    /** Use a JDK, by version, specified in the toolchains.xml file. */
    class ToolchainJdk(private val jdkVersion: String) : SelectJdk {
        override fun plugin(node: Node) {
            node.apply {
                "plugin" {
                    toolchainPlugin.place(this)
                    "executions" {
                        "execution" {
                            "goals" {
                                "goal" { -"toolchain" }
                            }
                        }
                    }
                    "configuration" {
                        "toolchains" {
                            "jdk" {
                                "version" { -jdkVersion }
                            }
                        }
                    }
                }
            }
        }
    }

    /** This only requires that a particular version of Java be present. */
    class EnforceJdk(private val javaVersion: String) : SelectJdk {
        override fun plugin(node: Node) {
            node.apply {
                "plugin" {
                    enforcerPlugin.place(this)
                    "executions" {
                        "execution" {
                            "id" { -"enforce-java" }
                            "goals" {
                                "goal" { -"enforce" }
                            }
                        }
                    }
                    "configuration" {
                        "rules" {
                            "requireJavaVersion" {
                                "version" { -javaVersion }
                            }
                        }
                    }
                }
            }
        }
    }
}
