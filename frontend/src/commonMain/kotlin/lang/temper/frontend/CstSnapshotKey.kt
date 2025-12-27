package lang.temper.frontend

import lang.temper.common.SnapshotKey
import lang.temper.cst.CstInner

/** Allows snapshotting the concrete syntax tree used during the parse stage. */
object CstSnapshotKey : SnapshotKey<CstInner> {
    override val databaseKeyText: String = "cst"
}
