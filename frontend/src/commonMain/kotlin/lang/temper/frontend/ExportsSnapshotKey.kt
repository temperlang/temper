package lang.temper.frontend

import lang.temper.common.SnapshotKey
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.interp.importExport.Export

object ExportsSnapshotKey : SnapshotKey<ExportList> {
    override val databaseKeyText: String = "exports"
}

class ExportList(val exports: List<Export>) : List<Export> by exports, Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        exports.forEach { it.destructure(this) }
    }
}
