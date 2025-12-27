package lang.temper.tooling

import kotlin.test.Test
import kotlin.test.assertEquals

class InitConfigTest {
    @Test
    fun autoNames() {
        // Given whatever dir name, we reformat for created file names and content.
        val dashed = """_"a"--HiThereSCUBAFolks__and.some things2you""".chimericToDash()
        assertEquals("a-hi-there-scuba-folks-and-some-things2-you", dashed)
        val titled = dashed.dashToTitle()
        assertEquals("A Hi There Scuba Folks And Some Things 2 You", titled)
    }
}
