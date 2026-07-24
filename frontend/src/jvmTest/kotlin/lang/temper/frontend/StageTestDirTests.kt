package lang.temper.frontend

import kotlin.test.Test
import kotlin.test.assertEquals

class StageTestDirTests {
    @Test
    fun stageTestDirFileRootAvailableAsFiles() {
        assertEquals("file", stageTestDirFileRoot.scheme)
    }
}
