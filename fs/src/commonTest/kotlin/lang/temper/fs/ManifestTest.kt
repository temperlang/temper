package lang.temper.fs

import kotlin.test.Test
import kotlin.test.assertContains

class ManifestTest {
    @Test
    fun checkManifestRuns() {
        val m = Manifests.manifestFor(ManifestTest::class)
        assertContains(m.implementationTitle ?: "<missing>", "opentest4j")
    }
}
