package lang.temper.be.java

import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.value.DependencyCategory

data class ModuleInfo(
    val packageName: QualifiedName,
    val module: ModuleName,
) {
    val lastPart = packageName.lastPartOrNull?.outputNameText ?: FALLABCK_MODULE_NAME
    val globalsClassName: OutName by lazy {
        val classString = lastPart.temperToJavaClass(MODULE_GLOBAL_SUFFIX)
        OutName(classString.assertSafe(), null)
    }
    fun qualifiedClassName(dependencyCategory: DependencyCategory): QualifiedName =
        packageName.qualify(
            when (dependencyCategory) {
                DependencyCategory.Production -> globalsClassName
                DependencyCategory.Test -> testClassName
            },
        )

    val samPkg: QualifiedName by lazy {
        packageName.qualifyKnownSafe(SAM_PACKAGE_NAME)
    }
    val entryClassName: OutName by lazy {
        val classString = lastPart.temperToJavaClass(MODULE_ENTRY_SUFFIX)
        OutName(classString.assertSafe(), null)
    }
    val entryQualifiedName: QualifiedName by lazy {
        packageName.qualify(entryClassName)
    }
    val testClassName: OutName by lazy {
        val classString = lastPart.temperToJavaClass(MODULE_TEST_SUFFIX)
        OutName(classString.assertSafe(), null)
    }
}
