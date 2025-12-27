package lang.temper.type

import lang.temper.common.Console
import lang.temper.common.toStringViaTextOutput
import lang.temper.frontend.implicits.ImplicitsModule
import kotlin.test.Test
import kotlin.test.assertEquals

class WellKnownTypesTest {
    /**
     * Test that supertype relationships before and after ImplicitsModule loaded
     * are the same.
     *
     * This allows other unit tests to be run even when [ImplicitsModule] fails to initialize
     * helping diagnose breaking changes to typing frontend code.
     */
    @Test
    fun checkSuperTypeRelationshipsFullySpecifiedByWellKnownTypes() {
        val allWellKnown = WellKnownTypes.allWellKnown.sortedBy { it.word!!.text }
        fun snapshotSuperTypeRelationships() = toStringViaTextOutput {
            Console(it).run {
                for (wellKnownTypeDef in allWellKnown) {
                    group("${wellKnownTypeDef.name}") {
                        for (tp in wellKnownTypeDef.typeParameters) {
                            group("<${tp.name}>") {
                                for (st in tp.definition.superTypes) {
                                    log("extends $st")
                                }
                            }
                        }
                        for (st in wellKnownTypeDef.superTypes) {
                            log("extends $st")
                        }
                    }
                }
            }
        }
        val superTypeRelationshipsBefore = snapshotSuperTypeRelationships()
        ImplicitsModule.module
        val superTypeRelationshipsAfter = snapshotSuperTypeRelationships()
        assertEquals(superTypeRelationshipsBefore, superTypeRelationshipsAfter)
    }
}
