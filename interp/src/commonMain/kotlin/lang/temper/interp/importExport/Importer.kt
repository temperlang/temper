package lang.temper.interp.importExport

import lang.temper.name.ExportedName
import lang.temper.name.ModuleLocation

/**
 * That which may import from an [Exporter].
 */
interface Importer {
    val loc: ModuleLocation

    /**
     * [createLocalBindingsForImport] calls this to record import relationships
     * which allows storing dependency graph information with *Module*s which
     * later compilation steps use to present *Backend*s with import information.
     */
    fun recordImportMetadata(importRecord: ImportRecord)

    sealed class ImportRecord {
        abstract val isBlockingImport: Boolean
        abstract val exporterLocation: ModuleLocation?
    }

    data class OkImportRecord(
        val imported: Set<ExportedName>,
        val exporter: Exporter,
        override val isBlockingImport: Boolean,
    ) : ImportRecord() {
        override val exporterLocation: ModuleLocation get() = exporter.loc
    }

    sealed class BadImportRecord : ImportRecord()

    data class UnresolvableImportRecord(
        val specifier: String,
        override val isBlockingImport: Boolean,
    ) : BadImportRecord() {
        override val exporterLocation: Nothing? get() = null
    }

    data class BrokenImportRecord(
        val exporter: Exporter,
        override val isBlockingImport: Boolean,
    ) : BadImportRecord() {
        override val exporterLocation: ModuleLocation get() = exporter.loc
    }
}
