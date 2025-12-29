package lang.temper.be.java

import lang.temper.be.tmpl.TmpL
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.value.DependencyCategory
import lang.temper.be.java.Java as J

/**
 * Represents entities that may be discovered by searching for a local name.
 * - Variables may need to be "lifted" into a local class so they can be captured by lambdas and mutated.
 * - Functions may likewise need to be lifted so they can self-recurse, or mutually recurse.
 *
 * This class describes how to address a local name, [asLhs] and [asExpr], but declaring them is handled by
 * the [JavaTranslator.ModuleScope.block] method.
 *
 * See [NameLift] for an overview of most of the subclasses.
 */
sealed class LocalName(val outName: OutName) {
    open val scopeName: OutName? get() = null
    open fun asLhs(pos: Position, names: JavaNames): J.LeftHandSide = error("not assignable")
    open fun asExpr(names: JavaNames, node: TmpL.Tree): J.Expression = outName.toNameExpr(node.pos)
    open val isRecursiveFunc: Boolean get() = false
    open val isMutablyCaptured: Boolean get() = false
    open fun liftName(lift: NameLift, scopeName: OutName): LocalName? = null
    val lift get() = when (this) {
        is ModuleLevelName -> NameLift.None
        is RegularVarName -> NameLift.RegularVar
        is CapturedMutableVarName -> NameLift.CapturedMutableVar
        is ThisCapturedMutableVarName -> NameLift.ThisCapturedMutableVar
        is SimpleFuncName -> NameLift.SimpleFunction
        is RecursiveFuncName -> NameLift.RecursiveFunction
        is FwdDeclFuncName -> NameLift.FwdDeclFunction
    }
}

/** Not a local name, but a module name found as a fallback in a local search. */
class ModuleLevelName(private val qualName: QualifiedName) : LocalName(qualName.lastPart) {
    override fun asExpr(names: JavaNames, node: TmpL.Tree) = qualName.toNameExpr(node.pos)
    override fun asLhs(pos: Position, names: JavaNames): J.NameExpr {
        val mod = names.currentModuleInfo
        if (mod != null) {
            val (head, tail) = qualName.split()
            if (head == mod.qualifiedClassName(DependencyCategory.Production) ||
                head == mod.qualifiedClassName(DependencyCategory.Test)
            ) {
                return tail.toNameExpr(pos)
            }
        }
        return qualName.toNameExpr(pos)
    }
}

/**
 * Explains how a name is lifted, and what to lift it to.
 */
enum class NameLift {
    None,

    /** Local var that is either not captured or already final. */
    RegularVar,

    /** Local var that is captured and mutable. */
    CapturedMutableVar,

    /** Local var that is captured and mutable when used in capturing closures. */
    ThisCapturedMutableVar,

    /** Local function declared that is not recursive. */
    SimpleFunction,

    /** Local function that is recursive but not called before definition. */
    RecursiveFunction,

    /** Local function that is recursive and must be forward declared. */
    FwdDeclFunction,
}

/**
 * Most locals, including arguments, are simply regular variables, especially this has some specific methods like
 * [asIdentifier] and [asRestFormal].
 */
class RegularVarName(
    outName: OutName,
    override val isMutablyCaptured: Boolean,
) : LocalName(outName) {
    override fun liftName(lift: NameLift, scopeName: OutName): LocalName? = when (lift) {
        NameLift.CapturedMutableVar -> CapturedMutableVarName(outName, scopeName)
        else -> null
    }
    override fun asLhs(pos: Position, names: JavaNames): J.LeftHandSide = outName.toNameExpr(pos)
    fun asIdentifier(pos: Position) = outName.toIdentifier(pos)
    fun asRestFormal(pos: Position) =
        OutName(outName.outputNameText + REST_SUFFIX, outName.sourceName)
            .toIdentifier(pos)
}

class CapturedMutableVarName(
    outName: OutName,
    override val scopeName: OutName,
) : LocalName(outName) {
    private fun nameExpr(pos: Position): J.NameExpr {
        return listOf(scopeName, outName).toNameExpr(pos)
    }

    override fun asExpr(names: JavaNames, node: TmpL.Tree) = nameExpr(node.pos)
    override fun asLhs(pos: Position, names: JavaNames) = nameExpr(pos)
}

class ThisCapturedMutableVarName(
    outName: OutName,
) : LocalName(outName) {
    private fun accessExpr(pos: Position): J.FieldAccessExpr {
        return J.ThisExpr(pos).field(outName)
    }

    override fun asExpr(names: JavaNames, node: TmpL.Tree) = accessExpr(node.pos)
    override fun asLhs(pos: Position, names: JavaNames) = accessExpr(pos)
}

class SimpleFuncName(
    outName: OutName,
    override val isRecursiveFunc: Boolean,
) : LocalName(outName) {
    override fun asLhs(pos: Position, names: JavaNames): J.LeftHandSide = outName.toNameExpr(pos)
    override fun liftName(lift: NameLift, scopeName: OutName): LocalName? = when (lift) {
        NameLift.RecursiveFunction -> RecursiveFuncName(outName, scopeName)
        NameLift.FwdDeclFunction -> FwdDeclFuncName(outName, scopeName)
        else -> null
    }
}

class RecursiveFuncName(
    outName: OutName,
    override val scopeName: OutName,
) : LocalName(outName) {
    override fun asExpr(names: JavaNames, node: TmpL.Tree) =
        J.InstanceMethodReferenceExpr(
            node.pos,
            if (names.isInScope(node, scopeName)) {
                J.ThisExpr(node.pos)
            } else {
                scopeName.toNameExpr(node.pos)
            },
            method = outName.toIdentifier(node.pos),
        )
}

class FwdDeclFuncName(
    outName: OutName,
    override val scopeName: OutName,
) : LocalName(outName) {
    override fun asExpr(names: JavaNames, node: TmpL.Tree) =
        J.InstanceMethodReferenceExpr(
            node.pos,
            if (names.isInScope(node, scopeName)) {
                J.ThisExpr(node.pos)
            } else {
                scopeName.toNameExpr(node.pos)
            },
            method = outName.toIdentifier(node.pos),
        )
}

/**
 * The [JavaTranslator.ModuleScope.block] method uses instances of this class to place [J.LocalClassDeclaration]s into
 * a list of statements and then add [J.ClassBodyDeclaration]s to them after the fact.
 */
class ScopeStmts(
    varAndClass: Pair<OutName, OutName>,
    pos: Position,
    val callsBefore: Set<ResolvedName>,
) {
    val scopeName: OutName = varAndClass.first
    val className: OutName = varAndClass.second
    private val classDecl: J.LocalClassDeclaration
    private val classDeclBody = mutableListOf<J.ClassBodyDeclaration>()

    fun addDecl(decl: J.ClassBodyDeclaration) {
        classDeclBody.add(decl)
    }

    /** Assign the class declarations to the scope class when the block is complete. */
    fun finish() {
        classDecl.body = classDeclBody
    }

    val statements: List<J.BlockLevelStatement>

    init {
        val classType = QualifiedName.make(listOf(className))
        classDecl = J.LocalClassDeclaration(
            pos,
            name = className.toIdentifier(pos),
            mods = J.LocalClassModifiers(pos),
            body = listOf(),
        )
        statements = listOf(
            classDecl,
            J.LocalVariableDeclaration(
                pos,
                type = classType.toClassType(pos),
                name = scopeName.toIdentifier(pos),
                mods = J.VariableModifiers(pos, modFinal = J.ModFinal.Final),
                expr = J.InstanceCreationExpr(
                    pos,
                    type = classType.toClassType(pos),
                    args = listOf(),
                ),
            ),
        )
    }
}
