package lang.temper.frontend

import lang.temper.common.SnapshotKey
import lang.temper.value.BlockTree

/** Allows snapshotting the current state of a module root. */
object AstSnapshotKey : SnapshotKey<BlockTree> {
    override val databaseKeyText: String = "ast"
}
