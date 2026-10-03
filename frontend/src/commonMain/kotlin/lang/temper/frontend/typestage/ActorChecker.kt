package lang.temper.frontend.typestage

import lang.temper.common.Log
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Type2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.isVoidLike
import lang.temper.type2.passTypeOf
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MetadataMap
import lang.temper.value.Tree
import lang.temper.value.actorSymbol
import lang.temper.value.imuSymbol
import lang.temper.value.isAssignment
import lang.temper.value.partialImuSymbol
import lang.temper.value.positionForKey
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeShapeAtLeafOrNull

enum class ActorMessage(
    override val formatString: String,
) : LeveledMessageTemplate {
    ActorMemberIsNotSendable(
        "Actor class %s: %s has type %s, which is not sendable",
    ),
    ActorMemberHasNonSendablePart(
        "Actor class %s: %s has type %s, which is not sendable because %s is not",
    ),
    ActorClassIsAlsoImu("Class %s cannot be both @actor and @%s"),
    ActorOnInterface("Interface %s cannot be @actor; only a class can"),
    ActorOnNonClass("@actor applies only to classes"),
    ;

    override val suggestedLevel: Log.Level = Log.Error
}

/**
 * Checks `@actor` classes.
 *
 * Calls into an actor run as turns, possibly on another thread, so only
 * sendable values may cross its boundary: every public property, and every
 * parameter and result of every public method, getter, setter and
 * constructor, must have a type that [DeepImmutability.findNonSendablePart]
 * accepts.  Private and protected members are reachable only from inside
 * the actor's own turns, so they are not checked.
 *
 * Also rejects `@actor` where it means nothing: on an interface, on a class
 * that is also `@imu` or `@partialImu`, and on anything that is not a class.
 */
class ActorChecker(
    private val logSink: LogSink,
) {
    private val immutability = DeepImmutability()

    fun check(root: Tree) {
        val funTreesByName = mutableMapOf<TemperName, FunTree>()
        val actorDecls = mutableListOf<Pair<Tree, MetadataMap>>()

        fun walk(tree: Tree) {
            when (tree) {
                is DeclTree -> tree.parts?.metadataSymbolMap?.let { metadata ->
                    if (actorSymbol in metadata) {
                        actorDecls.add(tree to metadata)
                    }
                }
                is FunTree -> tree.parts?.metadataSymbolMap?.let { metadata ->
                    if (actorSymbol in metadata) {
                        actorDecls.add(tree to metadata)
                    }
                }
                else -> if (isAssignment(tree)) {
                    val left = tree.child(1)
                    val right = tree.child(2)
                    if (left is LeftNameLeaf && right is FunTree) {
                        funTreesByName[left.content] = right
                    }
                }
            }
            for (child in tree.children) {
                walk(child)
            }
        }
        walk(root)

        for ((tree, metadata) in actorDecls.sortedBy { it.first.pos.left }) {
            val typeShape = if (tree is DeclTree) {
                metadata[typeDeclSymbol]?.target?.typeShapeAtLeafOrNull
            } else {
                null
            }
            if (typeShape == null) {
                logSink.log(
                    ActorMessage.ActorOnNonClass,
                    metadata.positionForKey(actorSymbol) ?: tree.pos,
                    emptyList(),
                )
            } else {
                check(typeShape, funTreesByName)
            }
        }
    }

    /**
     * Checks one type that claims `@actor`.
     *
     * @param funTreesByName maps method names to their definitions, so that
     *     a diagnostic about a parameter can point at the parameter.
     * @return true when [typeShape] passes.
     */
    fun check(typeShape: TypeShape, funTreesByName: Map<TemperName, FunTree> = emptyMap()): Boolean {
        if (actorSymbol !in typeShape.metadata) { return true }
        val typeName = typeShape.diagnosticTypeName
        val actorPos = typeShape.metadata.getEdges(actorSymbol).firstOrNull()?.let { edge ->
            edge.source?.childOrNull(edge.edgeIndex - 1)?.pos
        } ?: typeShape.pos

        if (typeShape.abstractness == Abstractness.Abstract) {
            logSink.log(ActorMessage.ActorOnInterface, actorPos, listOf(typeName))
            return false
        }

        var passes = true
        for (imuLike in listOf(imuSymbol, partialImuSymbol)) {
            if (imuLike in typeShape.metadata) {
                passes = false
                logSink.log(ActorMessage.ActorClassIsAlsoImu, actorPos, listOf(typeName, imuLike.text))
            }
        }

        val checkedPropertySymbols = buildSet {
            for (property in typeShape.properties) {
                // A computed property is checked through its getter and setter below.
                if (property.visibility != Visibility.Public || property.abstractness != Abstractness.Concrete) {
                    continue
                }
                val type = property.descriptor ?: continue
                add(property.symbol)
                val ok = checkSendable(
                    typeName,
                    "public property ${property.symbol.text}",
                    type,
                    property.declarationPos,
                )
                if (!ok) { passes = false }
            }
        }

        for (method in typeShape.methods) {
            if (method.visibility != Visibility.Public) { continue }
            when (method.methodKind) {
                MethodKind.Getter, MethodKind.Setter ->
                    // The implied getter or setter of a backed property checked above has its type.
                    if (method.symbol in checkedPropertySymbols) { continue }
                MethodKind.Normal, MethodKind.Constructor -> {}
            }
            if (!checkMethod(typeName, method, funTreesByName[method.name])) {
                passes = false
            }
        }
        return passes
    }

    private fun checkMethod(typeName: TemperName, method: MethodShape, funTree: FunTree?): Boolean {
        val sig = method.descriptor ?: return true
        val description = when (method.methodKind) {
            MethodKind.Normal -> "method ${method.symbol.text}"
            MethodKind.Getter -> "getter ${method.symbol.text}"
            MethodKind.Setter -> "setter ${method.symbol.text}"
            MethodKind.Constructor -> "constructor"
        }
        // Parameter names, formals and input types all start with `this` when there is one.
        val names = method.parameterInfo?.names
        val formals = funTree?.parts?.formals
        val skip = if (sig.hasThisFormal) 1 else 0

        var passes = true
        val inputTypes = sig.requiredInputTypes + sig.optionalInputTypes
        for ((i, inputType) in inputTypes.withIndex()) {
            if (i < skip) { continue }
            val name = names?.getOrNull(i)?.text ?: "#$i"
            val formal = formals?.getOrNull(i)
            val formalPos = formal?.pos ?: method.declarationPos
            val ok = checkSendable(typeName, "parameter $name of $description", inputType, formalPos)
            if (!ok) { passes = false }
        }

        val returnType = passTypeOf(sig.returnType2)
        if (!returnType.isVoidLike) {
            val returnTypePos = funTree?.parts?.returnDecl?.parts?.type?.target?.pos
                ?: method.declarationPos
            val ok = checkSendable(typeName, "the result of $description", returnType, returnTypePos)
            if (!ok) { passes = false }
        }
        return passes
    }

    private fun checkSendable(
        typeName: TemperName,
        what: String,
        type: Type2,
        pos: Position,
    ): Boolean {
        val nonSendablePart = immutability.findNonSendablePart(type) ?: return true
        if (nonSendablePart.definition == WellKnownTypes.invalidTypeDefinition) {
            // The typer has already explained why there is no type here.
            return false
        }
        if (nonSendablePart == type) {
            logSink.log(ActorMessage.ActorMemberIsNotSendable, pos, listOf(typeName, what, forDisplay(type)))
        } else {
            logSink.log(
                ActorMessage.ActorMemberHasNonSendablePart,
                pos,
                listOf(typeName, what, forDisplay(type), forDisplay(nonSendablePart)),
            )
        }
        return false
    }
}

/** Old-style types render function types as `fn (A): B` rather than as their functional interface. */
private fun forDisplay(type: Type2): Any = hackMapNewStyleToOld(type)
