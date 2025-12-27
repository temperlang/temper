package lang.temper.log

import lang.temper.common.MimeType

sealed class FilePathAndMimeTypeOrNull {
    abstract val filePath: FilePath
    abstract val mimeType: MimeType?

    companion object {
        operator fun invoke(
            filePath: FilePath,
            mimeType: MimeType?,
        ): FilePathAndMimeTypeOrNull = when (mimeType) {
            null -> FilePathNoMimeType(filePath)
            else -> FilePathAndMimeType(filePath = filePath, mimeType = mimeType)
        }
    }
}

private data class FilePathNoMimeType(
    override val filePath: FilePath,
) : FilePathAndMimeTypeOrNull() {
    override val mimeType: MimeType? get() = null

    override fun toString(): String = "($filePath)"
}

data class FilePathAndMimeType(
    override val filePath: FilePath,
    override val mimeType: MimeType,
) : FilePathAndMimeTypeOrNull() {
    override fun toString(): String = "($filePath, $mimeType)"
}
