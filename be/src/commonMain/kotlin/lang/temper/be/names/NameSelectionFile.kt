package lang.temper.be.names

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import lang.temper.fs.Manifests
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.QName

@Serializable
data class NameSelectionFile(
    @SerialName("about")
    val about: String = "",
    @SerialName("toolchain-version")
    val toolChainVersion: String = "",
    @Serializable(with = DashedIdentifierSerializer::class)
    @SerialName("library-name")
    val libraryName: DashedIdentifier?,
    @Serializable(with = BackendIdSerializer::class)
    @SerialName("backend-id")
    val backendId: BackendId,
    @SerialName("selections")
    val selections: List<NameSelection>,
) {
    fun toJsonString() = JsonCustom.encodeToString(this)

    /** Decorates an instance with about text and toolchain version information. */
    fun decorated(): NameSelectionFile {
        // Should be the mainfest for the CLI when deployed
        val data = Manifests.manifestFor(NameSelectionFile::class)
        val version: String =
            if (data.implementationTitle == "Temper") {
                "temper-${data.implementationVersion}"
            } else {
                null
            } ?: "<debug>"
        return this.copy(about = aboutText, toolChainVersion = version)
    }

    fun selectionsAsMap(): Map<QName, String> {
        return selections.mapNotNull { sel ->
            sel.qualifiedName?.let { it to sel.selectedName }
        }.toMap()
    }

    companion object {
        /** Constructs a NameSelectionFile structure. */
        fun make(
            libraryName: DashedIdentifier,
            backendId: BackendId,
            selections: List<NameSelection>,
        ) = NameSelectionFile(
            libraryName = libraryName,
            backendId = backendId,
            selections = selections,
        )

        fun fromJson(jsonText: String): NameSelectionFile =
            JsonCustom.decodeFromString(jsonText)

        private val JsonCustom = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        }
        const val aboutText =
            "Backend names selected by user via the temper CLI. This file should be committed."
    }
}

@Serializable
data class NameSelection(
    @Serializable(with = QNameSerializer::class)
    @SerialName("qualified-name")
    val qualifiedName: QName?,
    @SerialName("selected-name")
    val selectedName: String,
)

object DashedIdentifierSerializer : KSerializer<DashedIdentifier?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("lang.temper.name.DashedIdentifier", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): DashedIdentifier? {
        return DashedIdentifier.from(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: DashedIdentifier?) {
        encoder.encodeString(value!!.text)
    }
}

object BackendIdSerializer : KSerializer<BackendId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("lang.temper.name.BackendId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BackendId =
        BackendId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: BackendId) {
        encoder.encodeString(value.uniqueId)
    }
}

object QNameSerializer : KSerializer<QName?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("lang.temper.name.QName", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): QName? =
        QName.fromString(decoder.decodeString()).result

    override fun serialize(encoder: Encoder, value: QName?) {
        encoder.encodeString(value!!.toString())
    }
}
