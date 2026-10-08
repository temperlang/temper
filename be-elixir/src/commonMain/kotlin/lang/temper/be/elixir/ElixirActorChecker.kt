package lang.temper.be.elixir

import lang.temper.common.Log
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.MemberShape
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.SuperTypeTree2
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

internal enum class ElixirActorMessage(
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
 * Checks `@actor` classes before be-elixir translates them.
 *
 * An actor is a BEAM process, and a value crosses into it by copy, so only
 * sendable values may cross its boundary: every public property, and every
 * parameter and result of every public method, getter, setter and
 * constructor, must have a type that [findNonSendablePart] accepts. Private
 * and protected members are reachable only from inside the actor's own
 * turns, so they are not checked.
 *
 * Also rejects `@actor` where it means nothing: on an interface, on a class
 * that is also `@imu` or `@partialImu`, and on anything that is not a class.
 *
 * Other backends ignore `@actor` and run every call on the caller's stack,
 * so they accept these programs; the check lives here, not in the frontend.
 * The run-time check in temper-core stays: Elixir code calling an actor
 * directly is not type-checked by anyone.
 */
internal class ElixirActorChecker(
    private val logSink: LogSink,
) {
    private val superTypesCache = mutableMapOf<Type2, SuperTypeTree2<Type2>>()

    /**
     * Where a type has been reported. A constructor parameter that declares a
     * public property is both, at one position, and gets one message.
     */
    private val reported = mutableSetOf<Position>()

    /** Checks every `@actor` in one module's generated code. True if nothing was reported. */
    fun check(root: Tree): Boolean {
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

        var passes = true
        for ((tree, metadata) in actorDecls.sortedBy { it.first.pos.left }) {
            val typeShape = if (tree is DeclTree) {
                metadata[typeDeclSymbol]?.target?.typeShapeAtLeafOrNull
            } else {
                null
            }
            if (typeShape == null) {
                passes = false
                logSink.log(
                    ElixirActorMessage.ActorOnNonClass,
                    metadata.positionForKey(actorSymbol) ?: tree.pos,
                    emptyList(),
                )
            } else if (!check(typeShape, funTreesByName)) {
                passes = false
            }
        }
        return passes
    }

    /**
     * Checks one type that claims `@actor`.
     *
     * @param funTreesByName maps method names to their definitions, so that
     *     a diagnostic about a parameter can point at the parameter.
     * @return true when [typeShape] passes.
     */
    private fun check(typeShape: TypeShape, funTreesByName: Map<TemperName, FunTree>): Boolean {
        val typeName = typeShape.diagnosticTypeName
        val actorPos = typeShape.metadata.getEdges(actorSymbol).firstOrNull()?.let { edge ->
            edge.source?.childOrNull(edge.edgeIndex - 1)?.pos
        } ?: typeShape.pos

        if (typeShape.abstractness == Abstractness.Abstract) {
            logSink.log(ElixirActorMessage.ActorOnInterface, actorPos, listOf(typeName))
            return false
        }

        var passes = true
        for (imuLike in listOf(imuSymbol, partialImuSymbol)) {
            if (imuLike in typeShape.metadata) {
                passes = false
                logSink.log(ElixirActorMessage.ActorClassIsAlsoImu, actorPos, listOf(typeName, imuLike.text))
            }
        }

        val checkedPropertySymbols = buildSet {
            for (property in typeShape.properties) {
                // A computed property is checked through its getter and setter below.
                if (property.visibility != Visibility.Public || property.abstractness != Abstractness.Concrete) {
                    continue
                }
                val type = property.descriptor
                    ?: TODO("@actor class $typeName: public property ${property.symbol.text} has no type")
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
        val sig = method.descriptor
            ?: TODO("@actor class $typeName: public ${method.methodKind} ${method.symbol.text} has no signature")
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
            val name = names?.getOrNull(i)?.text
                ?: TODO("@actor class $typeName: parameter $i of $description has no name")
            val formalPos = formals?.getOrNull(i)?.pos ?: method.declarationPos
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
        val nonSendablePart = findNonSendablePart(type) ?: return true
        if (nonSendablePart.definition == WellKnownTypes.invalidTypeDefinition) {
            // The typer has already explained why there is no type here.
            return false
        }
        if (!reported.add(pos)) return false
        if (nonSendablePart == type) {
            logSink.log(
                ElixirActorMessage.ActorMemberIsNotSendable,
                pos,
                listOf(typeName, what, forDisplay(type)),
            )
        } else {
            logSink.log(
                ElixirActorMessage.ActorMemberHasNonSendablePart,
                pos,
                listOf(typeName, what, forDisplay(type), forDisplay(nonSendablePart)),
            )
        }
        return false
    }

    /**
     * Null if a value of [type] may cross an actor's boundary, otherwise
     * the first part of [type] that may not.
     *
     * A type is sendable when it is `@imu`, an `@actor` class, or a
     * `Promise` of a sendable type, and a `@partialImu` type such as `List`
     * or `Map` is sendable when its type arguments are. This is the frontend
     * ImuChecker's deep-immutability test with one change: it recurses into
     * type arguments with this rule, so `List<Counter>` of an actor passes,
     * where ImuChecker's own recursion would reject the actor.
     */
    private fun findNonSendablePart(type: Type2): Type2? {
        val superTypeTree = superTypesCache.getOrPut(type) { SuperTypeTree2.of(type) }
        if (superTypeTree.hasSymbol(actorSymbol) || superTypeTree.hasSymbol(imuSymbol)) {
            return null
        }
        if (type.definition == WellKnownTypes.promiseTypeDefinition) {
            return when (type.bindings.size) {
                1 -> findNonSendablePart(type.bindings[0])
                else -> type
            }
        }
        if (
            superTypeTree.hasSymbol(partialImuSymbol) &&
            // Type formals do not have parameters so PartialImu makes little sense there.
            type.definition is TypeShape
        ) {
            if (type.bindings.size != type.definition.formals.size) {
                return type
            }
            for (actual in type.bindings) {
                val problem = findNonSendablePart(actual)
                if (problem != null) {
                    return problem
                }
            }
            return null
        }
        return type
    }
}

private fun SuperTypeTree2<Type2>.hasSymbol(symbol: Symbol): Boolean =
    byDefinition.keys.any { it.metadata.containsKey(symbol) }

private val MemberShape.declarationPos: Position get() =
    this.stay?.pos ?: this.enclosingType.pos.leftEdge

private val TypeDefinition.diagnosticTypeName: TemperName get() =
    (this.name as? ResolvedParsedName)?.baseName ?: this.name

/** Old-style types render function types as `fn (A): B` rather than as their functional interface. */
private fun forDisplay(type: Type2): Any = hackMapNewStyleToOld(type)
