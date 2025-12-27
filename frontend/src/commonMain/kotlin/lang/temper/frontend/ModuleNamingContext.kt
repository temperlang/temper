package lang.temper.frontend

import lang.temper.common.AtomicCounter
import lang.temper.env.BindingNamingContext
import lang.temper.log.FilePath
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.LibraryNameLocationKey
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.TemperName
import lang.temper.stage.Stage

class ModuleNamingContext internal constructor(
    counter: AtomicCounter = AtomicCounter(),
    owner: Module,
) : BindingNamingContext(counter) {
    constructor(namingContext: NamingContext, owner: Module) : this(namingContext.adoptCounter(), owner)

    var owner: Module = owner
        private set

    override val locationDiagnostic: String
        get() {
            val loc = owner.loc
            val libraryName =
                owner.sharedLocationContext[loc, LibraryNameLocationKey]
            if (libraryName != null) {
                return buildString {
                    append('`')
                    append(libraryName.text)
                    when (loc) {
                        is ImplicitsCodeLocation -> {}
                        is ModuleName ->
                            if (loc.relativePath() != FilePath.emptyPath) {
                                append(UNIX_FILE_SEGMENT_SEPARATOR)
                                loc.appendRelativePathAndSuffix(this)
                            }
                    }
                    append('`')
                }
            }
            return super.locationDiagnostic
        }

    /**
     * Temporarily swap ownership to [interloper] for the duration of a run of [action]
     * so that a Module may stage a subordinate module to its same stage and incorporate
     * its content without having to rewrite stays.
     * Used during mixin processing and reincorporation.
     */
    fun <T> hijackTo(interloper: Module, action: () -> T): T {
        // Store
        val oldOwner = owner

        // Override
        this.owner = interloper

        val result = try {
            action()
        } finally {
            // Restore
            owner = oldOwner
        }
        return result
    }

    override val loc get() = owner.loc

    /**
     * Given the name of a top-level module declaration defined in this module,
     * returns the computed binding if available.
     *
     * Null if no binding is available, which is probably true for
     * [static property shape][lang.temper.type.StaticPropertyShape]s
     * before the end of [Stage.Define], and other bindings before
     * the end of [Stage.Run].
     */
    override fun getTopLevelBinding(name: TemperName) =
        owner.topLevelBindings?.localDeclarationMetadata(name)
    override val topLevelBindingNames get() = owner.topLevelBindings?.locallyDeclared ?: emptySet()
}
