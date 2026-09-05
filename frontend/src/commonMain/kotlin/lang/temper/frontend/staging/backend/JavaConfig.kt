package lang.temper.frontend.staging.backend

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.log.LogSink
import lang.temper.name.Symbol
import lang.temper.value.BlockTree
import lang.temper.value.Value

object JavaConfigKeys {
    /** Key for the be-java (and be-java8) config instance. */
    const val CONFIG = "java"

    /** The name of the class for configuring be-java. */
    const val CONFIG_CLASS_NAME = "JavaConfig"

    /** Config files may export a name with this text to specify the Maven library name */
    const val NAME = "name"
    val libraryNameGlobal = Symbol("javaName")

    /** Config files may export a name with this text to specify the Maven group id */
    const val GROUP = "group"
    val libraryGroupGlobal = Symbol("javaGroup")

    /** Config files may export a name with this text to specify the Maven artifact id */
    const val ARTIFACT = "artifact"
    val libraryArtifactGlobal = Symbol("javaArtifact")

    /** Config files may export a name with this text to specify the Java `package` name */
    const val PACKAGE = "package"
    val packageGlobal = Symbol("javaPackage")

    /** Config key to specify Maven dependencies */
    const val DEPENDENCIES = "dependencies"
    val dependenciesGlobal = Symbol("javaDependencies")
}

object JavaConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = JavaConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(
                    JavaConfigKeys.NAME to { V(Value(Types.string)) },
                    JavaConfigKeys.PACKAGE to { V(Value(Types.string)) },
                    JavaConfigKeys.GROUP to { V(Value(Types.string)) },
                    JavaConfigKeys.ARTIFACT to { V(Value(Types.string)) },
                    JavaConfigKeys.DEPENDENCIES to {
                        Call(BuiltinFuns.angleFn) {
                            V(Value(Types.list))
                            V(Value(Types.string))
                        }
                    },
                ),
            )
        }
    }
}
