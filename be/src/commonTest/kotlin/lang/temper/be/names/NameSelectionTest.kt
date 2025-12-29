package lang.temper.be.names

import lang.temper.common.orThrow
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.QName
import kotlin.test.Test
import kotlin.test.assertEquals

class NameSelectionTest {
    @Test
    fun nameSerialization() {
        val actual = NameSelectionFile.make(
            libraryName = DashedIdentifier.from("test-library")!!,
            backendId = BackendId("example"),
            selections = listOf(
                NameSelection(
                    qualifiedName = QName.fromString("class").orThrow(),
                    selectedName = "Class_",
                ),
                NameSelection(
                    qualifiedName = QName.fromString("class.field").orThrow(),
                    selectedName = "Field",
                ),
            ),
        ).decorated()
        assertEquals(
            """
            {
                "about": "${NameSelectionFile.aboutText}",
                "toolchain-version": "<debug>",
                "library-name": "test-library",
                "backend-id": "example",
                "selections": [
                    {
                        "qualified-name": "class",
                        "selected-name": "Class_"
                    },
                    {
                        "qualified-name": "class.field",
                        "selected-name": "Field"
                    }
                ]
            }
            """.trimIndent(),
            actual.toJsonString(),
        )
    }

    @Test
    fun nameDeserialization() {
        val json = """
            {
              "library-name": "test-library", "backend-id": "example",
              "selections": [
                { "qualified-name": "class", "selected-name": "Class_" },
                { "qualified-name": "class.field", "selected-name": "Field" }
              ]
            }
        """
        val actual = NameSelectionFile.fromJson(json)
        val expect = NameSelectionFile.make(
            libraryName = DashedIdentifier.from("test-library")!!,
            backendId = BackendId("example"),
            selections = listOf(
                NameSelection(
                    qualifiedName = QName.fromString("class").orThrow(),
                    selectedName = "Class_",
                ),
                NameSelection(
                    qualifiedName = QName.fromString("class.field").orThrow(),
                    selectedName = "Field",
                ),
            ),
        )
        assertEquals(expect, actual)
    }

    @Test
    fun nameDeserializationToleratesErrors() {
        val json = """
            {
              "library-name": "", "backend-id": "example",
              "selections": [
                { "qualified-name": " invalid", "selected-name": "Class_" },
                { "qualified-name": "class.field", "selected-name": "Field" }
              ]
            }
        """
        val actual = NameSelectionFile.fromJson(json)
        val expect = NameSelectionFile(
            libraryName = null,
            backendId = BackendId("example"),
            selections = listOf(
                NameSelection(
                    qualifiedName = null,
                    selectedName = "Class_",
                ),
                NameSelection(
                    qualifiedName = QName.fromString("class.field").orThrow(),
                    selectedName = "Field",
                ),
            ),
        )
        assertEquals(expect, actual)
    }
}
