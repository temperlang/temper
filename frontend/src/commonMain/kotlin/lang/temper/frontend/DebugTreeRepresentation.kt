package lang.temper.frontend

import lang.temper.common.Console
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.value.Tree
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode

@Suppress("unused") // Uses tend not to get committed.
internal enum class DebugTreeRepresentation {
    None {
        override fun dump(tree: Tree, dest: Console) {
            ignore(tree)
        }
    },
    PseudoCode {
        override fun dump(tree: Tree, dest: Console) {
            tree.toPseudoCode(dest.textOutput)
        }
    },
    Lispy {
        override fun dump(tree: Tree, dest: Console) {
            dest.log(tree.toLispy(multiline = true))
        }
    },
    Everything {
        override fun dump(tree: Tree, dest: Console) {
            Lispy.dump(tree, dest)
            PseudoCode.dump(tree, dest)
        }
    },
    ;

    abstract fun dump(
        tree: Tree,
        dest: Console = console,
    )
}
