package lang.temper.be

import lang.temper.be.tmpl.TestBackend
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendTest {
    private val helloWorldLibraryConfig = LibraryConfiguration(
        libraryName = DashedIdentifier.from("hello-world")!!,
        libraryRoot = dirPath("a"),
        supportedBackendList = emptyList(),
        classifyTemperSource = ::defaultClassifyTemperSource,
    )

    @Test
    fun backendOrganization() {
        class NeedyBackendFactory(backendId: String, requiredBackendIds: List<BackendId>) : TestBackend.TestFactory() {
            override val backendId: BackendId = BackendId(backendId)
            override val backendMeta = super.backendMeta.copy(
                backendId = this.backendId,
                requiredBackendIds = requiredBackendIds,
            )
        }
        val missingBackendId = BackendId("missing")
        val needyFactory = NeedyBackendFactory("needy", listOf(TestBackend.backendId, missingBackendId))
        val needlessFactory = NeedyBackendFactory("needless", listOf())
        val needierFactory = NeedyBackendFactory(
            "needier",
            listOf(needyFactory.backendId, needlessFactory.backendId),
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
        val missingBackendIds = mutableSetOf<BackendId>()
        val organization = organizeBackends(
            backendIds = listOf(needierFactory.backendMeta.backendId, requestedAloofFactory.backendId),
            lookupFactory = { backends[it] },
            onMissingFactory = { missingBackendIds.add(it) },
        )
        assertEquals(setOf(missingBackendId), missingBackendIds)
        // Collections maintain order by default, so these should be reliable.
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
}
