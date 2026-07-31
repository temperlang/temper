package lang.temper.be

import lang.temper.ast.OutTree
import lang.temper.be.tmpl.TestBackend
import lang.temper.be.tmpl.TmpL
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.name.BackendId
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BackendTest {
    @Test
    fun backendAdjusters() {
        val tmpl = TmplGenerator(".test")
        // Define some adjusters and factories.
        class TestAdjusterA : BackendAdjuster {
            override fun <T : OutTree<*>> adjustConnectedCall(decl: TmpL.FunctionDeclaration, call: T): T? {
                // Abusively just treat ids as calls to simplify.
                call is TmpL.Id || return null
                return when (call.name.displayName) {
                    "_" -> {
                        @Suppress("UNCHECKED_CAST")
                        return tmpl.makeId(BuiltinName("there")) as T
                    }
                    else -> null
                }
            }
            override fun <T : OutTree<*>> adjustFilesAfterTranslation(files: MutableList<T>) {
                @Suppress("UNCHECKED_CAST")
                files.add(tmpl.makeId(BuiltinName("a")) as T)
            }
        }
        class TestAdjusterB : BackendAdjuster {
            override fun <T : OutTree<*>> adjustConnectedCall(decl: TmpL.FunctionDeclaration, call: T): T? {
                return call
            }
            override fun <T : OutTree<*>> adjustFilesAfterTranslation(files: MutableList<T>) {
                @Suppress("UNCHECKED_CAST")
                files.add(tmpl.makeId(BuiltinName("b")) as T)
            }
        }
        class TestAdjusterFactory(val adjuster: BackendAdjuster) : BackendAdjusterFactory {
            override fun makeAdjuster(module: TmpL.Module): BackendAdjuster = adjuster
        }
        val comboFactory = TestAdjusterFactory(TestAdjusterA())
            .orElse(TestAdjusterFactory(TestAdjusterB()))
        // Work on some tmpl.
        val module = tmpl.module {
            moduleFunction(BuiltinName("hi")) {}
        }
        val adjuster = comboFactory.makeAdjuster(module)
        val function = module.topLevels.first() as TmpL.FunctionDeclaration
        fun adjustCall(name: String): String {
            val id = adjuster.adjustConnectedCall(function, tmpl.makeId(BuiltinName(name)))
            return id!!.name.displayName
        }
        // The first adjuster only replaces name "_". The second always returns what it's given.
        assertEquals("there", adjustCall("_"))
        assertEquals("yall", adjustCall("yall"))
        // Now see how we finalize, expecting reverse order.
        assertContentEquals(
            listOf("b", "a"),
            buildList<TmpL.Id> { adjuster.adjustFilesAfterTranslation(this) }.map { it.name.displayName },
        )
    }

    @Test
    fun backendOrganization() {
        class NeedyBackendFactory(
            backendId: String,
            requiredBackendIds: List<BackendId>,
            val adjusterFactories: Map<BackendId, BackendAdjusterFactory> = mapOf(),
        ) : TestBackend.TestFactory() {
            override val backendId: BackendId = BackendId(backendId)
            override val backendMeta = super.backendMeta.copy(
                backendId = this.backendId,
                requiredBackendIds = requiredBackendIds,
            )
            override fun adjusterFactories(): Map<BackendId, BackendAdjusterFactory> {
                return adjusterFactories
            }
        }
        val missingBackendId = BackendId("missing")
        val needyFactory = NeedyBackendFactory("needy", listOf(TestBackend.backendId, missingBackendId))
        val needlessFactory = NeedyBackendFactory("needless", listOf())
        val uselessAdjuster = object : BackendAdjuster {}
        val uselessAdjusterFactory = object : BackendAdjusterFactory {
            override fun makeAdjuster(module: TmpL.Module): BackendAdjuster = uselessAdjuster
        }
        val needierFactory = NeedyBackendFactory(
            backendId = "needier",
            requiredBackendIds = listOf(needyFactory.backendId, needlessFactory.backendId),
            adjusterFactories = mapOf(
                needyFactory.backendId to uselessAdjusterFactory,
                // We don't directly require this, so we shouldn't be adjusting it.
                TestBackend.backendId to uselessAdjusterFactory,
            ),
        )
        val aloofBackend = NeedyBackendFactory("aloof", listOf())
        val requestedAloofFactory = NeedyBackendFactory("requested-aloof", listOf())
        val backends = listOf<Backend.Factory<TestBackend>>(
            TestBackend.Factory,
            needyFactory,
            needlessFactory,
            needierFactory,
            aloofBackend,
            requestedAloofFactory,
        ).associateBy { it.backendId }
        val errors = mutableSetOf<BackendOrganizationError>()
        val organization = organizeBackends(
            backendIds = listOf(needierFactory.backendMeta.backendId, requestedAloofFactory.backendId),
            lookupFactory = { backends[it] },
            onError = { err -> errors.add(err) },
        )
        // Collections maintain order by default, so these should be reliable.
        assertEquals(
            setOf(
                BackendOrganizationError(
                    kind = BackendOrganizationErrorKind.FactoryNotFound,
                    backendId = missingBackendId,
                ),
                BackendOrganizationError(
                    kind = BackendOrganizationErrorKind.AdjusterForUnrequiredBackend,
                    backendId = TestBackend.backendId,
                    sourceBackendId = needierFactory.backendId,
                ),
            ),
            errors,
        )
        assertEquals(
            mapOf(
                needierFactory.backendId to setOf(
                    needierFactory.backendId,
                    needyFactory.backendId,
                    needlessFactory.backendId,
                    TestBackend.backendId,
                ),
                requestedAloofFactory.backendId to setOf(requestedAloofFactory.backendId),
                needyFactory.backendId to setOf(needyFactory.backendId, TestBackend.backendId),
                needlessFactory.backendId to setOf(needlessFactory.backendId),
                TestBackend.backendId to setOf(TestBackend.backendId),
            ),
            organization.backendRequirements,
        )
        assertEquals(
            listOf(
                listOf(requestedAloofFactory.backendId, needlessFactory.backendId, TestBackend.backendId),
                listOf(needyFactory.backendId),
                listOf(needierFactory.backendId),
            ),
            organization.backendBuckets,
        )
        assertEquals(backends.keys - setOf(aloofBackend.backendId), organization.factoriesById.keys)
        assertEquals(uselessAdjusterFactory, organization.adjusterFactories[needyFactory.backendId])
    }

    @Test
    fun nonPrefaceOutPath() {
        val moduleName = ModuleName(
            sourceFile = filePath("a", "b", "c.temper"),
            libraryRootSegmentCount = 1,
            isPreface = false,
        )
        assertEquals(
            filePath("b", "c.out"),
            Backend.defaultFilePathForSource(helloWorldLibraryConfig, moduleName, ".out"),
        )
    }

    @Test
    fun nonPrefaceDirOutPath() {
        val moduleName = ModuleName(
            sourceFile = dirPath("a", "b"),
            libraryRootSegmentCount = 1,
            isPreface = false,
        )
        assertEquals(
            filePath("b.out"),
            Backend.defaultFilePathForSource(helloWorldLibraryConfig, moduleName, ".out"),
        )
    }

    @Test
    fun prefaceOutPath() {
        val moduleName = ModuleName(
            sourceFile = filePath("a", "b", "c.temper"),
            libraryRootSegmentCount = 1,
            isPreface = true,
        )
        // A case where a module dir path turns into a file path.
        assertEquals(
            filePath("b", "c_preface.out"),
            Backend.defaultFilePathForSource(helloWorldLibraryConfig, moduleName, ".out"),
        )
    }

    @Test
    fun prefaceDirOutPath() {
        val moduleName = ModuleName(
            sourceFile = dirPath("a", "b"),
            libraryRootSegmentCount = 1,
            isPreface = true,
        )
        // A case where a module dir path turns into a file path.
        assertEquals(
            filePath("b_preface.out"),
            Backend.defaultFilePathForSource(helloWorldLibraryConfig, moduleName, ".out"),
        )
    }

    private val helloWorldLibraryConfig = LibraryConfiguration(
        libraryName = DashedIdentifier.from("hello-world")!!,
        libraryRoot = dirPath("a"),
        supportedBackendList = emptyList(),
        classifyTemperSource = ::defaultClassifyTemperSource,
    )
}
