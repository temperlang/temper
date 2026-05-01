package lang.temper.frontend

import lang.temper.builtin.Types
import lang.temper.env.InterpMode
import lang.temper.log.Position
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.BlockTree
import lang.temper.value.CallableValue
import lang.temper.value.DeclTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.MacroEnvironment
import lang.temper.value.PartialResult
import lang.temper.value.StayLeaf
import lang.temper.value.StaylessMacroValue
import lang.temper.value.firstInOrder
import lang.temper.value.insertBeforeAll
import lang.temper.value.staySymbol
import lang.temper.value.topLevelMetadataSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.unpackPositionedOr
import lang.temper.value.void

/**
 * A placeholder declaration to which metadata for the module as a whole can be added.
 * This allows for use-cases similar to those for file-level annotations in other languages.
 */
fun requireTopLevelModuleMetadata(module: Module, root: BlockTree): DeclTree {
    val preExistingStay = module.topLevelMetadataStay
    if (preExistingStay != null) {
        val parent = preExistingStay.incoming?.source
        if (parent is DeclTree) {
            return parent
        }
    }
    // Create a top level declaration if none already present.
    val pos = Position(module.loc, 0, 0)
    val stay = StayLeaf(root.document, pos)
    val name = root.document.nameMaker.unusedTemporaryName("moduleMetadata")
    val decl = root.treeFarm.grow(pos) {
        Decl {
            Ln(name)
            V(topLevelMetadataSymbol)
            V(void)
            V(typeSymbol)
            V(Types.vEmpty)
            V(staySymbol)
            Replant(stay)
        }
    }
    insertBeforeAll(root, setOf(firstInOrder(root))) {
        Replant(decl)
    }

    // Store it so multiple macro runs don't create independent decls
    module.topLevelMetadataStay = stay

    return decl
}

internal class AddTopLevelMetadataImpl(
    val module: Module,
    val root: BlockTree,
) : CallableValue, StaylessMacroValue {
    override val sigs: List<Signature2>? = null
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (key, value) = args.unpackPositionedOr(2, cb) {
            return@invoke it
        }
        val decl = requireTopLevelModuleMetadata(module, root)
        decl.insert {
            V(key)
            V(value)
        }
        if (cb is MacroEnvironment && decl.incoming?.breadcrumb == null) {
            // Avoid never visited problems
            cb.evaluateEdge(decl.incoming!!, InterpMode.Partial)
        }
        return void
    }

    override fun toString(): String = "AddTopLevelMetadataImpl"
}
