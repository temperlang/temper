package lang.temper.be.rust

import kotlin.test.Test
import kotlin.test.assertEquals

class RustNamesTest {
    @Test
    fun crateNameIsCargos() {
        // Cargo names a package's library crate by replacing each `-` with `_`, and nothing else.
        assertEquals("hello_world", PackageNaming("hello-world").crateName)
        assertEquals("f64str", PackageNaming("f64str").crateName)
        assertEquals("radix_36", PackageNaming("radix-36").crateName)
    }
}
