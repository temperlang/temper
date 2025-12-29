package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.common.SnapshotKey
import lang.temper.common.console
import lang.temper.common.isNotEmpty
import lang.temper.common.mapFirst
import lang.temper.common.soleElementOrNull
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.frontend.rw.Read
import lang.temper.frontend.rw.ReadsAndWrites
import lang.temper.frontend.rw.Write
import lang.temper.frontend.rw.WriteKind
import lang.temper.frontend.rw.lineFor
import lang.temper.frontend.rw.writesLive
import lang.temper.frontend.syntax.isAssignment
import lang.temper.log.LogConfigurations
import lang.temper.log.MessageTemplate
import lang.temper.log.Positioned
import lang.temper.name.InternalModularName
import lang.temper.name.ModuleLocation
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.StableTemperName
import lang.temper.name.Symbol
import lang.temper.name.Temporary
import lang.temper.type.BindMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type.isVoidLike
import lang.temper.type.isVoidLikeButNotOldStyleNever
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.Document
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MaximalPath
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.StayLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.blockChildReferenceToStmt
import lang.temper.value.failSymbol
import lang.temper.value.freeTree
import lang.temper.value.fromTypeSymbol
import lang.temper.value.functionContained
import lang.temper.value.isImplicits
import lang.temper.value.ssaSymbol
import lang.temper.value.syntheticSymbol
import lang.temper.value.toPseudoCode
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typePlaceholderSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void

private const val DEBUG = false

@Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
private inline val Document.debugging get() = DEBUG && !this.isImplicits
private inline fun Document.debug(message: () -> Any?) {
    if (debugging) {
        val o = message()
        if (o != Unit) {
            console.log("$o")
        }
    }
}

internal class CleanupTemporaries private constructor(
    private val module: Module,
    private val root: BlockTree,
    private val beforeResultsExplicit: Boolean,
    private val inputParameters: List<DeclTree>,
    private val returnDecl: DeclTree?,
    private val typeInfo: Map<ResolvedName, StaticType?>,
) {
    internal object DebugAstSnapshotKey : SnapshotKey<DataTablesList> {
        override val databaseKeyText: String get() = "debugCleanupTemporaries"
    }

    private var readsAndWrites: ReadsAndWrites = ReadsAndWrites.zeroValue

    /**
     * A required name is one that we must not eliminate.
     * Specifically, a name is required if it:
     * - is not an InternalModularName, or
     * - is a function parameter name, or
     * - is a result name (like `return__123`), or
     * - has a declaration with a declared type or other important declaration metadata
     */
    private var requiredNames: Set<ResolvedName> = emptySet()

    fun clean(): DataTables {
        root.document.debug {
            console.group("Before") {
                root.toPseudoCode(
                    console.textOutput,
                    detail = PseudoCodeDetail(elideFunctionBodies = true),
                )
            }
        }
        readsAndWrites =
            ReadsAndWrites.forRoot(module, root, inputParameters, returnDecl)
        requiredNames = computeRequiredNames(readsAndWrites)

        // Now, we try a sequence of strategies to find edits to perform.
        // If one possible edit list is nonempty, we don't try to find
        // additional edits because readsAndWrites might be invalidated
        // by the first group.
        var edits: List<Edit>

        edits = simplifyVoidAssignments(readsAndWrites)
        if (edits.isEmpty() && !beforeResultsExplicit) {
            edits = eliminateNoopReads(readsAndWrites)
        }
        if (edits.isEmpty()) {
            edits = collapseWritesToSingleName(readsAndWrites)
        }
        if (edits.isEmpty()) {
            edits = eliminateWritesUpstreamOfNothing(readsAndWrites)
        }
        if (edits.isEmpty()) {
            edits = collapseWritesLiveOnlyForAnAssignment(readsAndWrites)
        }
        if (edits.isEmpty()) {
            edits = inlineAdjacentSingleReadWritePairs(readsAndWrites)
        }
        if (edits.isEmpty()) {
            edits = inlineStaticReads(readsAndWrites)
        }
        if (edits.isEmpty()) {
            edits = eliminateUnusedDeclarations(readsAndWrites)
        }
        if (edits.isEmpty()) {
            val namesThatNeedVar = mayNeedVar.toSet()
            mayNeedVar.clear()
            edits = fixupVarAtEnd(namesThatNeedVar, readsAndWrites)
        }
        if (edits.isEmpty()) {
            edits = flagProblems()
        }

        performEdits(edits)
        return DataTables(readsAndWrites, edits)
    }

    private val MaximalPath.Element.edge: TEdge? get() = root.dereference(ref)

    /** True when the containing flow element is a reference to `void`. */
    private val MaximalPath.Element.isNoop: Boolean get() {
        val edge = root.dereference(ref) ?: return false
        val tree = edge.target
        return tree is ValueLeaf && tree.content == void
    }

    sealed class Edit(
        /** For debugging purposes, the primary line number affected. */
        private val lineNo: Int,
        /** For debugging purposes, a human-readable description of the effect. */
        private val description: String,
    ) : Structured {
        override fun destructure(structureSink: StructureSink) {
            structureSink.value("${this@Edit}")
        }

        override fun toString(): String =
            "${this@Edit::class.simpleName}(L$lineNo: $description)"
    }

    class Replace(
        lineNo: Int,
        description: String,
        val edgeToReplace: TEdge,
        val createReplacement: Planting.() -> UnpositionedTreeTemplate<*>,
    ) : Edit(lineNo, description)

    class SplitAssignment(
        lineNo: Int,
        description: String,
        val write: Write,
        val simplerRightHandSide: Planting.() -> UnpositionedTreeTemplate<*>,
    ) : Edit(lineNo, description)

    class AddMetadata(
        lineNo: Int,
        description: String,
        val decl: DeclTree,
        val metadataKey: Symbol,
        val metadataValue: Value<*>,
        val metadataType: StaticType,
    ) : Edit(lineNo, description)

    private fun simplifyVoidAssignments(readsAndWrites: ReadsAndWrites): List<Edit> = buildList {
        val editsBuilder = this

        for ((name, writesToName) in readsAndWrites.writes) {
            val type = typeInfo[name]
            if (type?.isVoidLikeButNotOldStyleNever == true) {
                for (write in writesToName) {
                    if (write.writeKind != WriteKind.SimpleAssignment) { continue }
                    val stmt = write.containingPathElement ?: continue
                    val assignment = stmt.edge?.target ?: continue
                    val assigned = (assignment as CallTree).child(2)
                    val assignedType = assigned.typeInferences?.type
                    if (
                        // Do not mask type failures
                        assignedType?.isVoidLike == false ||
                        // and do not try to simplify if already simple enough
                        (assigned as? ValueLeaf)?.content == void
                    ) {
                        continue
                    }
                    editsBuilder.add(
                        SplitAssignment(
                            lineNo = lineFor(stmt.ref),
                            description = "split void assignment of $name",
                            write = write,
                        ) { V(void, WellKnownTypes.voidType) },
                    )
                }
            }
        }
    }

    private val mayNeedVar = mutableSetOf<ResolvedName>()
    private fun collapseWritesToSingleName(readsAndWrites: ReadsAndWrites): List<Edit> = buildList {
        val editListBuilder = this

        // If there is a name x such that all reads of it are in writes to y, and there are no other
        // writes to y, then we can either replace x with y.
        //
        //     x = 1;
        //     ...
        //     y = x;
        //     f(y)
        //
        // The above could be simplified by eliminating
        // writes to `y`, and then replacing `x` with `y`.
        //
        //     y = 1;
        //     ...
        //     ;      // was write to y
        //     f(y)
        //
        // or by eliminating writes to `y` and then replacing `y` with `x`.
        //
        //    x = 1;
        //    ...
        //    ;
        //    f(x)
        //
        // CAVEAT: sequential writes to `x` with intermediate reads of `y`.
        //
        //    x = f();
        //    y = x;
        //    x = g();
        //    console.log(y); // should log result of f()
        //    y = x;
        //    console.log(y);
        //
        // CAVEAT: there's a similar problem when `y` is referenced outside
        // the declaring function, and there is more than one assignment to
        // `x` then we can't optimize.
        //
        //    x = f();
        //    y = x;
        //    let useY() { console.log(y); }
        //    x = g();
        //    useY();
        //    y = x;
        //
        // So, the below looks for name pairs as labelled above where
        // - every read of `x` is written to `y`.
        // - every write to `y` is a read of `x`.
        // - no write to `y` is live during a read of `x`.
        // - all reads of `x` and `y` are in the same function body
        //   in which `x` and `y` are defined.
        //
        // For each such pair, we'll choose to eliminate either `x` or `y` or abort:
        // - if `x` and `y` are both required name (see elsewhere), eliminate neither, else
        // - if `x` is a required name, and `y` is not, eliminate `y`, else
        // - if `y` is a required name, and `x` is not, eliminate `x`, else
        // - if `x` is a Temporary and `y` is not, eliminate `x`, else
        // - if `y` is a Temporary and `x` is not, eliminate `y`, else
        // - eliminate `y`.
        //
        // Finally, if the preserved name is not `var` and the eliminated
        // name is `var` and any write of the eliminated name is upstream of
        // another write to that `var`, then we need to make the preserved
        // name `var`.

        // identify pairs
        val pairs = mutableListOf<Pair<ResolvedName, ResolvedName>>()
        val names = readsAndWrites.localNames
        for (y in names) {
            val writesOfY = readsAndWrites.writes[y] ?: continue
            val x = writesOfY.firstOrNull()?.assigned?.name ?: continue
            if (x == y || x !in readsAndWrites.localNames) { continue }

            // every read of `x` is written to `y`.
            // every write to `y` is a read of `x`.
            val readsOfX = readsAndWrites.reads[x]
            if (
                readsOfX == null ||
                readsOfX.size < writesOfY.size ||
                writesOfY.any {
                    it.writeKind != WriteKind.SimpleAssignment ||
                        it.assigned?.name != x
                }
            ) {
                continue
            }
            pairs.add(x to y)
        }

        // choose strategy for pairs
        val pairsAndStrategies = pairs.mapNotNull { (x, y) ->
            val strategy = renameStrategyFor(
                x = x,
                y = y,
                readsAndWrites = readsAndWrites,
                canRenameXToYExtra = {
                    val readsOfY = readsAndWrites.reads[y] ?: emptyList()
                    // no write to `x` is live during a read of `y`
                    // that does not have a live write of `y` that also
                    // has that write of `x` live.
                    val elementsWithReadsOfY = mutableSetOf<MaximalPath.Element>()
                    readsOfY.mapNotNullTo(elementsWithReadsOfY) { it.containingPathElement }
                    elementsWithReadsOfY.all { elementContainingReadOfY ->
                        val yLiveForRead = readsAndWrites.writesLive(y, elementContainingReadOfY)
                        val writesToXLiveDuringWritesToY = buildSet {
                            yLiveForRead.forEach { write ->
                                val statement = write.containingPathElement
                                if (write.name == y && statement != null && write.assigned?.name == x) {
                                    // It is a `y = x` write.
                                    // Add the things live during it to the set so the set contains
                                    // the writes that are live when a `y = x` (that is itself live during
                                    // a read of y) is live.
                                    readsAndWrites.writesLive(x, statement).forEach {
                                        add(it)
                                    }
                                }
                            }
                        }
                        // Every write to `x` is live during one of the writes to `y`.
                        // TODO: Is liveness enough here, or do we need every writeToX is dominated
                        // by a `y = x`.
                        val writesToXLiveDuringReadOfY =
                            readsAndWrites.writesLive(x, elementContainingReadOfY).toSet()
                        writesToXLiveDuringWritesToY == writesToXLiveDuringReadOfY
                    }
                },
                canRenameYToXExtra = {
                    val readsOfX = readsAndWrites.reads[x] ?: emptyList()
                    // no write to `y` is live during a read of `x`.
                    !readsOfX.any { readOfX ->
                        val elementContainingRead = readOfX.containingPathElement
                        elementContainingRead != null &&
                            readsAndWrites.writesLive(y, elementContainingRead).isNotEmpty()
                    }
                },
            )
            strategy?.let { Triple(x, y, it) }
        }

        val touched = mutableSetOf<ResolvedName>()
        // If we have a chain of changes, like
        //    t#0,  t#1,  rename-y-to-x
        //    t#1,  t#2,  rename-y-to-x
        //    t#2,  t#3,  rename-y-to-x
        //    ...
        //    t#98, t#99, rename-y-to-x
        // then some edits below might conflict with others.
        // We can keep a set of names and, once we generate edits for
        //    t#0,  t#1,  rename-y-to-x
        // add t#0 and t#1 to the list.
        // Then we don't generate edits for
        //    t#1,  t#2,  rename-y-to-x
        // but the next time though, we'll generate edits for the
        // skipped ones based on ReadsAndWrites that take into account
        // the non-conflicting edits.
        // So at most, this avoids interference and adds one pass.

        // generate edits based on strategy
        for ((x, y, strategy) in pairsAndStrategies) {
            if (x in touched || y in touched) { continue }

            when (strategy) {
                RStrategy.RenameXToY -> {
                    val writesToX = readsAndWrites.writes[x] ?: emptyList()

                    // If we're rewriting multiple writes to use a different name, that
                    // name might need to be a `var` name.
                    when (NeedsVar.doesYNeedVar(x, y, readsAndWrites, writesToX, eliminatedY = { true })) {
                        NeedsVar.Ok -> {}
                        NeedsVar.YNeedsVar -> mayNeedVar.add(y)
                        NeedsVar.Impossible -> continue // Abort the rename
                    }

                    // Each write of x changes to y
                    writesToX.forEach { writeOfX ->
                        val assignment = writeOfX.containingPathElement!!.edge!!.target as CallTree
                        val assigned = assignment.edge(1)
                        val type = assigned.target.typeInferences?.type
                        editListBuilder.add(
                            Replace(
                                lineNo = lineFor(assigned.target),
                                description = "rename written $x to $y",
                                edgeToReplace = assigned,
                            ) {
                                Ln(y, type = type)
                            },
                        )
                    }
                    // Each read of x not used in writing to y needs to be renamed too.
                    readsAndWrites.reads[x]?.forEach { readOfX ->
                        val edge = readOfX.tree?.incoming ?: return@forEach
                        val parent = edge.source
                        if (
                            isAssignment(parent) && parent?.edge(2) == edge &&
                            (parent.child(1) as? LeftNameLeaf)?.content == y
                        ) {
                            return@forEach
                        }
                        editListBuilder.add(
                            Replace(
                                lineNo = lineFor(edge.target),
                                description = "rename read $x to $y",
                                edgeToReplace = edge,
                            ) {
                                Rn(y, type = edge.target.typeInferences?.type)
                            },
                        )
                    }
                }
                RStrategy.RenameYToX -> {
                    check(strategy == RStrategy.RenameYToX)
                    // Each read of y changes to x
                    readsAndWrites.reads[y]?.forEach { readOfY ->
                        val readEdge = readOfY.tree!!.incoming!!
                        val type = readEdge.target.typeInferences?.type
                        editListBuilder.add(
                            Replace(
                                lineNo = lineFor(readEdge.target),
                                description = "rename read $y to $x",
                                edgeToReplace = readEdge,
                            ) {
                                Rn(x, type = type)
                            },
                        )
                    }
                    // Writes of y are eliminated below
                }
            }

            // Writes of y can become void
            readsAndWrites.writes.getValue(y).forEach { writeOfY ->
                val writeEdge = writeOfY.containingPathElement!!.edge!!
                editListBuilder.add(
                    Replace(
                        lineNo = lineFor(writeEdge.target),
                        description = "$y=... -> no-op",
                        edgeToReplace = writeEdge,
                    ) {
                        V(void, WellKnownTypes.voidType)
                    },
                )
            }
            touched.add(x)
            touched.add(y)
            // Let separate passes sweep up obviated declarations to x or y
        }
    }

    private fun renameStrategyFor(
        x: ResolvedName,
        y: ResolvedName,
        readsAndWrites: ReadsAndWrites,
        /**
         * Called, lazily and at most once, to compute whether intermediate
         * writes would invalidate replacing some left-hand uses of [x] with
         * [y] based on the scope of the expected changes.
         */
        canRenameXToYExtra: () -> Boolean,
        /**
         * Called, lazily and at most once, to compute whether intermediate
         * writes would invalidate replacing some right-hand uses of [y] with
         * [x] based on the scope of the expected changes.
         */
        canRenameYToXExtra: () -> Boolean,
    ): RStrategy? {
        // See the comments above about renaming x to y or y to x
        val canRenameXToY = lazy(LazyThreadSafetyMode.NONE) {
            // writes to x are all assignments so the name can just change
            readsAndWrites.writes[x]?.all { it.writeKind.isAssignment } == true &&
                // y is not declared late
                !declaredAfterAssignment(declared = y, assigned = x, readsAndWrites = readsAndWrites) &&
                // Check for intermediate writes using a strategy tuned to the scope
                // of the expected change.
                canRenameXToYExtra()
        }
        val canRenameYToX = lazy(LazyThreadSafetyMode.NONE) {
            canRenameYToXExtra()
        }

        val xIsRequired = x in requiredNames
        val yIsRequired = y in requiredNames
        val xIsTemporary = x is Temporary
        val yIsTemporary = y is Temporary
        return when {
            // If writes to y are not simple assignments, then we can't
            // eliminate them without semantic risk.
            readsAndWrites.writes[y]?.all { it.writeKind == WriteKind.SimpleAssignment } == false -> null
            // if `x` and `y` are both required name (see below), eliminate neither, else
            xIsRequired && yIsRequired -> null
            // if `x` is a required name, and `y` is not, eliminate `y`, else
            xIsRequired -> if (canRenameYToX.value) { RStrategy.RenameYToX } else { null }
            // if `y` is a required name, and `x` is not, eliminate `x`, else
            yIsRequired -> if (canRenameXToY.value) { RStrategy.RenameXToY } else { null }
            // if `x` is a Temporary and `y` is not, eliminate `x`, else
            xIsTemporary && !yIsTemporary && canRenameXToY.value -> RStrategy.RenameXToY
            // if `y` is a Temporary and `x` is not, eliminate `y`, else
            !xIsTemporary && yIsTemporary && canRenameYToX.value -> RStrategy.RenameYToX
            canRenameYToX.value -> RStrategy.RenameYToX
            canRenameXToY.value -> RStrategy.RenameXToY
            else -> null
        }
    }

    private fun eliminateNoopReads(readsAndWrites: ReadsAndWrites) = buildList {
        val editListBuilder = this

        for (name in readsAndWrites.localNames) {
            if (name in requiredNames) {
                // Don't eliminate reads the IDE might need info for.
                // If the user types
                //
                //    foo
                //
                // and we want to be able to auto-suggest `.bar()`, we
                // need the LSP server to be able to locate the read of
                // `foo` and get useful metadata.  That means leaving
                // dead reads of ResolvedParsedNames around.
                // TODO: Should we have an IDE-running bit that lets us do better when
                // not running inside an LSP server?
                continue
            }
            val readsOfName = readsAndWrites.reads[name] ?: continue
            for (read in readsOfName) {
                // If the read is at the statement level,
                // then it's not actually doing any work.

                // TODO: we need to replace reads that may not have
                // been initialized with an error node earlier.

                val containing = read.containingPathElement ?: continue
                if (containing.isCondition) {
                    // branch condition, not a statement
                    continue
                }
                val ref = containing.ref
                val tree = read.tree ?: continue
                val edge = root.dereference(ref) ?: continue
                if (edge.target == tree) {
                    editListBuilder.add(
                        Replace(
                            lineNo = lineFor(tree),
                            description = "read $name -> no-op",
                            edgeToReplace = edge,
                        ) {
                            V(void, WellKnownTypes.voidType)
                        },
                    )
                }
            }
        }
    }

    private fun eliminateWritesUpstreamOfNothing(readsAndWrites: ReadsAndWrites) = buildList {
        val editListBuilder = this

        nameLoop@
        for (name in readsAndWrites.localNames) {
            // Stores to exported names, type members, and other required names
            // are important even if there's no downstream reads.
            if (name in requiredNames) { continue }
            val decls = readsAndWrites.declarations[name] ?: emptyList()
            if (decls.any { it.parts?.metadataSymbolMultimap?.contains(typePlaceholderSymbol) == true }) {
                continue
            }
            val isVar = decls.all { it.parts?.metadataSymbolMultimap?.contains(varSymbol) == true }
            val writesOfName = readsAndWrites.writes[name] ?: continue
            val deadForName = mutableListOf<TEdge>()
            for (write in writesOfName) {
                if (write.writeKind != WriteKind.SimpleAssignment) {
                    // TODO: can we eliminate hs calls whose fail vars are unchecked
                    continue
                }
                // Don't convert invalid code into valid code by eliminating dead writes.
                val writingElement = write.containingPathElement
                if (!isVar && writingElement != null) {
                    val liveAtWriteToName = readsAndWrites.writesLive(name, writingElement)
                    if (liveAtWriteToName.isNotEmpty()) {
                        continue@nameLoop
                    }
                }
                if (readsAndWrites.downstream[write].isNullOrEmpty()) {
                    // Nothing reads the value that was written.
                    // We can replace `x = ...` with `...`
                    val writeEdge = write.containingPathElement?.edge ?: continue
                    val assigned = (writeEdge.target as CallTree).child(2)
                    if (assigned is FunTree) {
                        // do not eliminate canonical names for functions that might
                        // have been inlined as values because having those names is
                        // super nice for TmpL translation.
                        continue
                    }
                    deadForName.add(writeEdge)
                }
            }
            for (writeEdge in deadForName) {
                val assigned = (writeEdge.target as CallTree).child(2)
                editListBuilder.add(
                    Replace(
                        lineNo = lineFor(writeEdge.target),
                        description = "simplify dead-store of $name",
                        edgeToReplace = writeEdge,
                    ) {
                        if (assigned is ValueLeaf || assigned is NameLeaf) {
                            V(void, WellKnownTypes.voidType) // If there's no side effect, it can be a no-op.
                        } else {
                            Replant(freeTree(assigned))
                        }
                    },
                )
            }
        }
    }

    /**
     * Like [collapseWritesToSingleName] but considers whether we
     * can eliminate single writes instead of whole names.
     */
    private fun collapseWritesLiveOnlyForAnAssignment(
        readsAndWrites: ReadsAndWrites,
    ): List<Edit> = buildList {
        // This is like the other name collapsing  but
        val editListBuilder = this
        val localNames = readsAndWrites.localNames

        // Consider (x, y) name there is an assignment to x that is only read
        // in an assignment to y, and that write is the only write to x that
        // is live for that read.
        //
        //     x = ...;
        //     ... // Does not read nor write x, or read y.
        //     y = x;
        //     ... // No reads of x here
        //
        // Where that declaration of
        //
        //     y = ...;
        //     ...
        //     ;
        //     ...
        for (x in localNames) {
            if (x !is Temporary) { continue }
            val writesToX = readsAndWrites.writes[x] ?: continue
            for (writeToX in writesToX) {
                val writeToXTree = writeToX.tree
                if (writeToXTree == null || writeToX.writeKind != WriteKind.SimpleAssignment) { continue }
                val readsOfX = readsAndWrites.downstream[writeToX]
                if (readsOfX?.size == 1) {
                    val readOfX = readsOfX.first() as? Read ?: continue
                    // If the readOfX is used in a simple assignment, then we're in business.
                    val (writeToYTree, y) = simpleAssignedForRead(readOfX, root) ?: continue
                    // Adjusting single names would put us in a situation where
                    // the while-last-edit-list-not-empty loop infinitely loops
                    // by making a change and then reversing it in the next
                    // cycle.
                    // Since x is a Temporary and y is not, this is monotonic so
                    // does not risk that.
                    if (y is Temporary) { continue }
                    val strategy = renameStrategyFor(
                        x = x,
                        y = y,
                        readsAndWrites = readsAndWrites,
                        canRenameXToYExtra = canRenameXToY@{
                            // The way we test for intermediate writes when replacing all `x` with `y`
                            // is different from how we should when dealing with one read and one write.
                            //
                            //    x = ...;
                            //    use(y);
                            //    y = x;
                            //
                            // If there is a read of y that is upstream of the assignment to y and
                            // downstream of the assignment to x, we can't turn that into:
                            //
                            //    y = ...;
                            //    use(y);
                            //    ;
                            //
                            // Below, answer the question:
                            // Is there a readOfY such that
                            // there is a writeToX upstream of readToY
                            // that is also upstream of the readOfX in a writeToY.
                            // If so, we cannot rename.

                            val readsOfY = readsAndWrites.reads[y]
                            if (!readsOfY.isNullOrEmpty()) {
                                // What's written at `y = x`?
                                val readOfXElement = readOfX.containingPathElement
                                // The writes to x upstream of that read of x.
                                val writesOfX = readsAndWrites.upstream[readOfX]
                                if (writesOfX != null && readOfXElement != null) {
                                    val writesToXLiveAtReadOfX = readsAndWrites.writesLive(x, readOfXElement)
                                    if (writesToXLiveAtReadOfX.any { it == writeToX }) {
                                        // writesOfX includes all the `x = ...` above.
                                        for (readOfY in readsOfY) {
                                            val element = readOfY.containingPathElement ?: continue
                                            val liveAtReadOfY = readsAndWrites.writesLive(x, element)
                                            if (liveAtReadOfY.any { it == writeToX }) {
                                                return@canRenameXToY false
                                            }
                                        }
                                    }
                                }
                            }
                            true
                        },
                        canRenameYToXExtra = { false },
                    )
                    if (strategy == RStrategy.RenameXToY) {
                        when (
                            NeedsVar.doesYNeedVar(
                                x = x,
                                y = y,
                                readsAndWrites,
                                listOf(writeToX),
                                eliminatedY = { it.tree == writeToYTree },
                            )
                        ) {
                            NeedsVar.Ok -> {}
                            NeedsVar.YNeedsVar -> mayNeedVar.add(y)
                            NeedsVar.Impossible -> continue
                        }
                        // x = ...;  ->  y = ...
                        editListBuilder.add(
                            Replace(
                                lineNo = lineFor(writeToXTree),
                                description = "rename written $x to $y",
                                edgeToReplace = writeToXTree.edge(1),
                            ) {
                                Ln(y, type = writeToXTree.typeInferences?.type)
                            },
                        )
                        // y = x;  ->  ;
                        editListBuilder.add(
                            Replace(
                                lineNo = lineFor(writeToYTree),
                                description = "$y = ... -> no-op",
                                edgeToReplace = writeToYTree.incoming!!,
                            ) {
                                V(void, type = WellKnownTypes.voidType)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun inlineAdjacentSingleReadWritePairs(
        readsAndWrites: ReadsAndWrites,
    ): List<Edit> = buildList {
        val editListBuilder = this

        // Turn
        //
        //     t#0 = f(x);
        //     g(t#0)
        //
        // into
        //
        //     g(f(x))
        //
        // We do this by looking for single reads that are
        // - downstream of one write
        // - which is upstream of that one read
        // - and which are close; the only thing computed between
        //   the end of the write and the read are ValueLeaves, const NameLeaves,
        //   or other no-op, order-independent things like static field reads.
        //   For example, the read of `g` is a no-op and `f(x)` cannot change its value,
        //   so we can reorder `f(x)` after it.

        for (name in readsAndWrites.localNames) {
            if (name in requiredNames || name !is Temporary) { continue }
            val readsOfName = readsAndWrites.reads[name] ?: continue

            for (read in readsOfName) {
                val upstream = readsAndWrites.upstream[read]
                val write = upstream?.soleElementOrNull
                val writeElement = write?.containingPathElement
                // If there is one write upstream, and it's covered
                if (
                    writeElement == null ||
                    write.writeKind != WriteKind.SimpleAssignment
                ) {
                    continue
                }
                // and that read is the only read downstream, then we should check for adjacency.
                val readsDownstream = readsAndWrites.downstream[write]
                if (readsDownstream?.size != 1 || readsDownstream.first() != read) {
                    continue
                }
                // and inlining should not prevent having a name for an anonymous function
                // which TmpLTranslator prefers.
                val assignmentEdge = writeElement.edge!!
                val assigned = assignmentEdge.target.child(2)
                if (assigned is FunTree || mustRemainAtStatementLevel(assigned)) {
                    continue
                }

                // Find the element after the upstream write.  If it's the element containing
                // the read, then we check the tree to find intervening nodes.
                val targetElement = read.containingPathElement
                if (targetElement?.pathIndex != writeElement.pathIndex) { continue }
                val nextElement: MaximalPath.Element? = run {
                    val path = readsAndWrites.paths[writeElement.pathIndex]
                    var elementIndex = path.elements.indexOf(writeElement)
                    while (0 <= elementIndex && elementIndex < path.elements.lastIndex) {
                        val next = path.elements[elementIndex + 1]
                        if (!next.isNoop) { break }
                        elementIndex += 1
                    }
                    if (elementIndex == -1) {
                        null
                    } else if (elementIndex + 1 < path.elements.size) {
                        path.elements[elementIndex + 1]
                    } else {
                        path.followers.getOrNull(0)?.condition
                    }
                }
                if (targetElement != nextElement) { continue }

                // Walk from tree towards looking down and to the left.
                // If the context from root looks like the below, then
                // A, B, and C are the preceding, non-containing trees.
                //
                // (CallTree
                //    A
                //    B
                //    (CallTree
                //       C
                //       READ
                //       X)
                //    Y
                //    Z)
                //
                // If the preceding, non-containing trees do not have a
                // side effect then we can replace READ with the
                // right-hand-side of the upstream write.
                fun mayReorderOver(t: Tree): Boolean = when (t) {
                    is FunTree -> true
                    is ValueLeaf -> true
                    is StayLeaf -> true
                    is CallTree -> {
                        // conservatively may not, but allow for some patterns:
                        // - do_bind_methodName(subject) where subject can be reordered over
                        // - nym`<>`(callee, TypeActuals) where callee can be reordered over
                        val callee = t.childOrNull(0)?.functionContained
                        when {
                            callee == BuiltinFuns.angleFn && t.size >= 2 -> mayReorderOver(t.child(1))
                            callee is GetStaticOp -> true
                            callee is DotHelper && callee.memberAccessor is BindMemberAccessor -> {
                                val subject = t.childOrNull(
                                    callee.memberAccessor.enclosingTypeIndexOrNegativeOne + 2,
                                )
                                subject != null && mayReorderOver(subject)
                            }
                            else -> false
                        }
                    }
                    is EscTree -> false // conservatively
                    is BlockTree -> t.children.all { mayReorderOver(it) }
                    is NameLeaf -> when (val nameAtLeaf = t.content) {
                        is StableTemperName -> true
                        else -> { // If it's effectively const, its value can't be changed by reordering
                            val decl = readsAndWrites.declarations[nameAtLeaf]?.soleElementOrNull
                            val metadata = decl?.parts?.metadataSymbolMultimap
                            metadata?.contains(ssaSymbol) == true
                        }
                    }
                    is DeclTree -> true // Odd seeing you here
                }
                // We can skip the reorder check for things like static
                // reads which are always const and don't invoke getters.
                var mayReorder = true // Looking for counter-evidence below
                val readContainer = read.containingPathElement.edge!!.target
                var edge = read.tree?.incoming ?: continue
                while (mayReorder) {
                    if (edge.target == readContainer) { break }
                    // Look at preceding siblings
                    val indexInParent = edge.edgeIndex
                    val parent = edge.source!! // Safe since we don't go above root
                    for (precedingSiblingIndex in 0 until indexInParent) {
                        val precedingSibling = parent.child(precedingSiblingIndex)
                        if (!mayReorderOver(precedingSibling)) {
                            mayReorder = false
                            break
                        }
                    }

                    edge = parent.incoming!!
                }

                if (mayReorder) {
                    // Turn the assignment into a no-op
                    editListBuilder.add(
                        Replace(
                            lineNo = lineFor(assignmentEdge.target),
                            description = "inlined assignment of $name -> no-op",
                            edgeToReplace = assignmentEdge,
                        ) {
                            V(void, type = WellKnownTypes.voidType)
                        },
                    )
                    // Move the assigned in place of the read
                    editListBuilder.add(
                        Replace(
                            lineNo = lineFor(assigned),
                            description = "inline value assigned to $name at sole read",
                            edgeToReplace = read.tree.incoming!!,
                        ) {
                            Replant(freeTree(assigned))
                        },
                    )
                }
            }
        }
    }

    private fun inlineStaticReads(readsAndWrites: ReadsAndWrites) = buildList<Edit> {
        val editListBuilder = this

        // Turn
        //
        //     t#0 = f(x);
        //     g(t#0)
        //
        // into
        //
        //     g(f(x))
        //
        // We do this by looking for single reads that are
        // - downstream of one write
        // - which is upstream of that one read
        // - and which are close; the only thing computed between
        //   the end of the write and the read are ValueLeaves, const NameLeaves,
        //   or other no-op, order-independent things like static field reads.
        //   For example, the read of `g` is a no-op and `f(x)` cannot change its value,
        //   so we can reorder `f(x)` after it.

        for (name in readsAndWrites.localNames) {
            if (name in requiredNames || name !is Temporary) {
                continue
            }
            val readsOfName = readsAndWrites.reads[name] ?: continue

            for (read in readsOfName) {
                val readEdge = read.tree?.incoming ?: continue
                val upstream = readsAndWrites.upstream[read]
                val write = upstream?.soleElementOrNull
                val writeElement = write?.containingPathElement
                // If there is one write upstream, and it's covered
                if (
                    writeElement == null ||
                    write.writeKind != WriteKind.SimpleAssignment
                ) {
                    continue
                }
                val assignmentEdge = writeElement.edge!!
                val assigned = assignmentEdge.target.child(2)
                if (!isGetStaticCall(assigned)) {
                    continue
                }
                // and that read is the only read downstream, then we should check if it's a GetStatic
                // macro call.
                val readsDownstream = readsAndWrites.downstream[write]
                if (readsDownstream?.size != 1 || readsDownstream.first() != read) {
                    continue
                }

                // Turn the write into a no-op.
                editListBuilder.add(
                    Replace(
                        lineNo = lineFor(assigned),
                        description = "${read.name} = getStatic -> noOp",
                        edgeToReplace = assignmentEdge,
                    ) {
                        V(void, WellKnownTypes.voidType)
                    },
                )
                // Inline the getStatic call where it's needed.
                editListBuilder.add(
                    Replace(
                        lineNo = read.lineNo,
                        description = "${read.name} -> getStatic(...)",
                        edgeToReplace = readEdge,
                    ) {
                        Replant(freeTree(assigned))
                    },
                )
            }
        }
    }

    private fun eliminateUnusedDeclarations(readsAndWrites: ReadsAndWrites) = buildList {
        val editListBuilder = this

        for (name in readsAndWrites.localNames) {
            if (name in requiredNames) { continue }
            val readsOfName = readsAndWrites.reads[name] ?: emptyList()
            val writesOfName = readsAndWrites.writes[name] ?: emptyList()
            if (readsOfName.isEmpty() && writesOfName.isEmpty()) {
                readsAndWrites.declarations[name]?.forEach { declTree ->
                    // We know it's not required above, so we can eliminate this.
                    editListBuilder.add(
                        Replace(
                            lineNo = lineFor(declTree),
                            description = "let $name -> no-op",
                            edgeToReplace = declTree.incoming!!,
                        ) {
                            V(void, WellKnownTypes.voidType)
                        },
                    )
                }
            }
        }
    }

    private fun fixupVarAtEnd(
        mayNeedVar: Set<ResolvedName>,
        readsAndWrites: ReadsAndWrites,
    ): List<Edit> = buildList {
        val editListBuilder = this

        for (name in mayNeedVar) {
            if (NeedsVar.stillHasWriteThatNeedsVar(name, readsAndWrites)) {
                val decl = readsAndWrites.declarations[name]?.soleElementOrNull ?: continue
                editListBuilder.add(
                    AddMetadata(
                        lineNo = lineFor(decl),
                        description = "make $name var",
                        decl = decl,
                        metadataKey = varSymbol,
                        metadataValue = void,
                        metadataType = WellKnownTypes.voidType,
                    ),
                )
            }
        }
    }

    private fun performEdits(edits: List<Edit>): Unit = console.groupIf(root.document.debugging, "Applying edits") {
        root.document.debug {
            console.group("Edits") {
                edits.forEach { console.log("$it") }
            }
        }
        // Cache finding a pointer into the ControlFlow to edit
        val blockReferenceToStmt = lazy(LazyThreadSafetyMode.NONE) {
            structureBlock(root).blockChildReferenceToStmt()
        }
        for (edit in edits) {
            when (edit) {
                is Replace -> {
                    val edgeToReplace = edit.edgeToReplace
                    val createReplacement = edit.createReplacement
                    edgeToReplace.replace {
                        createReplacement()
                    }
                }

                is AddMetadata -> {
                    val decl = edit.decl
                    decl.replace(decl.size until decl.size) {
                        V(decl.pos.rightEdge, Value(edit.metadataKey), type = Types.symbol.type)
                        V(decl.pos.rightEdge, edit.metadataValue, type = edit.metadataType)
                    }
                }

                is SplitAssignment -> {
                    val write = edit.write
                    val pathElement = write.containingPathElement!!
                    val assignment = pathElement.edge!!.target as CallTree
                    val rightEdge = assignment.edge(2)
                    val oldRight = rightEdge.target

                    // Simplify the assignment
                    rightEdge.replace {
                        edit.simplerRightHandSide.invoke(this)
                    }

                    // Add back any side effect.
                    val oldRightRef = BlockChildReference(root.size, oldRight.pos)
                    root.add(oldRight)

                    // Re-insert oldRight into the flow
                    val stmt = blockReferenceToStmt.value.getValue(pathElement.ref)
                    stmt.parent!!.withMutableStmtList { stmtList ->
                        val index = stmtList.indexOf(stmt)
                        stmtList.add(index, ControlFlow.Stmt(oldRightRef))
                    }
                }
            }
        }
        root.document.debug {
            console.group("After") {
                root.toPseudoCode(
                    console.textOutput,
                    detail = PseudoCodeDetail(elideFunctionBodies = true),
                )
            }
        }
    }

    private fun flagProblems(): List<Edit> = buildList {
        // If there is any reassignment of a const variable, flag it.
        for ((name, writes) in readsAndWrites.writes) {
            val decls = readsAndWrites.declarations[name] ?: continue
            val nonConstDecl = decls.firstOrNull {
                it.parts?.metadataSymbolMultimap?.contains(varSymbol) == false
            } ?: continue

            for (write in writes) {
                val location = write.containingPathElement ?: continue
                val live = readsAndWrites.writesLive(name, location)
                val preceding = live.firstOrNull()
                val precedingPos = preceding?.let { it.containingPathElement?.pos ?: it.tree?.pos }
                if (precedingPos != null) {
                    val declPos = nonConstDecl.pos
                    if (name != readsAndWrites.outputName) {
                        module.logSink.log(
                            Log.Error,
                            MessageTemplate.IllegalReassignment,
                            location.pos,
                            // {name} reassigned after {pos} but not declared `var` at {declPos}
                            listOf(name, precedingPos, declPos),
                        )
                    }

                    // We flagged the problem as an error which spikes compilation,
                    // but adding the metadata allows processing to continue in a way
                    // that doesn't break interpretation in the REPL, and prevents
                    // repeated processing of this declaration if CleanupTemporaries runs again.
                    add(
                        AddMetadata(
                            lineFor(declPos),
                            "Make problem declaration `var`",
                            nonConstDecl,
                            metadataKey = varSymbol,
                            metadataValue = void,
                            metadataType = WellKnownTypes.voidType,
                        ),
                    )
                }
            }
        }
    }

    private fun lineFor(p: Positioned) = module.lineFor(p)

    companion object {
        fun cleanup(
            module: Module,
            moduleRoot: BlockTree,
            beforeResultsExplicit: Boolean = false,
            outputName: ResolvedName? = null,
            snapshotId: LogConfigurations? = null,
        ) {
            // Capture type info for temporaries from declarations so that, given a typed tree,
            // the tree after cleanup is typed.
            val typeInfo = typeMapFor(moduleRoot)
            val allDataTables = mutableListOf<DataTables>()
            // Find each module and function root.
            for (oneRoot in allRootsOf(moduleRoot)) {
                check(oneRoot is BlockTree)
                val inputParameters: List<DeclTree>
                val returnDecl: DeclTree?
                when (val parent = oneRoot.incoming?.source) {
                    is FunTree -> {
                        val parts = parent.parts
                        inputParameters = (parts?.formals ?: emptyList()) +
                            listOfNotNull(parts?.restFormal?.tree)
                        returnDecl = parts?.returnDecl
                    }
                    null -> {
                        inputParameters = emptyList()
                        returnDecl = (outputName ?: module.outputName)?.let {
                            oneRoot.children.mapFirst { rootChild ->
                                if (rootChild is DeclTree && rootChild.parts?.name?.content == it) {
                                    rootChild
                                } else {
                                    null
                                }
                            }
                        }
                    }
                    else -> error("Neither module nor function body")
                }

                val cleaner = CleanupTemporaries(
                    module = module,
                    root = oneRoot,
                    beforeResultsExplicit = beforeResultsExplicit,
                    inputParameters = inputParameters,
                    returnDecl = returnDecl,
                    typeInfo = typeInfo,
                )

                while (true) {
                    val dataTables = cleaner.clean()
                    val madeProgress = dataTables.edits.isNotEmpty()
                    allDataTables.add(dataTables)
                    if (!madeProgress) {
                        break
                    }
                }
            }
            snapshotId?.let { id ->
                val tableList = DataTablesList(allDataTables, module.loc)
                module.console.snapshot(DebugAstSnapshotKey, id.loggerName, tableList)
            }
        }
    }

    internal data class DataTables(
        val readsAndWrites: ReadsAndWrites,
        val edits: List<Edit>,
    ) : Structured {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("readsAndWrites") {
                readsAndWrites.destructure(this)
            }
            key("edits") {
                arr {
                    edits.forEach { it.destructure(this) }
                }
            }
        }
    }

    internal data class DataTablesList(
        val dataTables: List<DataTables>,
        val loc: ModuleLocation,
    ) : Structured {
        override fun destructure(structureSink: StructureSink) =
            structureSink.value(dataTables)

        operator fun get(index: Int) = dataTables[index]

        val indices get() = dataTables.indices
    }

    private fun declaredAfterAssignment(
        declared: ResolvedName,
        assigned: ResolvedName,
        readsAndWrites: ReadsAndWrites,
    ): Boolean {
        // Renaming x to y involves changing the assigned name, not the read name.
        //     x = ...
        //     ...
        //     y = x
        //     ...
        //     use(y)
        // becomes
        //     y = ...
        //     ...
        //     void; // Eliminated y = ...
        //     ...
        //     use(y)
        // If the declaration of y occurs after the assignment of x, then we
        // would turn
        //     x = ...
        //     let y;
        //     y = x
        //     use(y)
        // into
        //     y = ...
        //     let y;
        //     void;
        //     use(y)
        // which has initialization before declaration.
        // This captures whether
        // TODO: If y's declaration could be moved earlier
        // (without affecting references in its declaration metadata for example)
        // we could include a strategy that moves its declaration.

        // If y has a declaration that is live during writing of x, then we
        // have a problem.
        val decls = readsAndWrites.declarations[declared] ?: return false
        if (decls.size != 1) {
            return true // conservatively
        }
        val decl = decls.first()
        val declEdge = decl.incoming
        if (declEdge?.source != root) {
            // Return declaration and input parameters may be declared outside the body.
            return false
        }
        val declRef = BlockChildReference(declEdge.edgeIndex, decl.pos)
        val declElement = readsAndWrites.blockChildrenToPathElements.getValue(declRef)
        val liveAtDeclaration = readsAndWrites.writesLive(assigned, declElement)
        return liveAtDeclaration.isNotEmpty()
    }
}

fun isNestedFunctionBody(t: Tree): Boolean {
    val incoming = t.incoming
    val parent = incoming?.source
    if (parent !is FunTree) { return false }
    return parent.lastChild === t
}

private fun canMakeVar(declParts: DeclParts, readsAndWrites: ReadsAndWrites): Boolean {
    val name = declParts.name.content
    if (name !is InternalModularName) {
        return false
    }
    // Do not muck across function bodies.
    if (name !in readsAndWrites.localNames) {
        return false
    }
    // Do not muck with function or module signatures.
    if (name in readsAndWrites.inputNames || name == readsAndWrites.outputName) {
        return false
    }
    val metadataMap = declParts.metadataSymbolMultimap
    return (fromTypeSymbol !in metadataMap && typeDeclSymbol !in metadataMap)
}

/** Strategy for eliminating uses of a name by renaming one name to another. */
private enum class RStrategy {
    RenameXToY,
    RenameYToX,
}

/** See documentation of [CleanupTemporaries.requiredNames]. */
private fun computeRequiredNames(
    readsAndWrites: ReadsAndWrites,
): Set<ResolvedName> = buildSet {
    addAll(readsAndWrites.inputNames)
    readsAndWrites.outputName?.let { add(it) }

    readsAndWrites.localNames.filterTo(this) { localName ->
        when (localName) {
            // Exported and builtin names can't go anywhere because other
            // modules may need them.
            !is InternalModularName -> true
            // We could eliminate these names but the IDE needs them and
            // the readability of translated code often depends upon them.
            is SourceName -> true
            is Temporary -> {
                val decls = readsAndWrites.declarations[localName]
                    ?: emptyList()
                // Does its declaration have important metadata like a type
                // that could affect subsequent applications of type
                // inference.
                decls.any { decl ->
                    (decl.parts?.metadataSymbolMultimap ?: emptyMap())
                        .keys.any { metadataKey ->
                            metadataKey !in okToEliminateDeclMetadataKeys
                        }
                }
            }
        }
    }
}
private val okToEliminateDeclMetadataKeys =
    setOf(failSymbol, ssaSymbol, syntheticSymbol, varSymbol)

private fun simpleAssignedForRead(read: Read, root: BlockTree): Pair<Tree, ResolvedName>? {
    val tree = read.tree ?: return null
    val edge = tree.incoming
    val parent = edge?.source
    if (parent is CallTree && isAssignment(parent) && edge == parent.edgeOrNull(2)) {
        val ref = read.containingPathElement!!.ref // Since we know tree is non-null
        if (parent.incoming == root.dereference(ref)) { // is not nested in an hs(...) call
            val left = parent.child(1)
            if (left is LeftNameLeaf) {
                val leftName = left.content as ResolvedName
                return parent to leftName
            }
        }
    }
    return null
}

private enum class NeedsVar {
    /** No `var` adjustment needed */
    Ok,

    /** Need to add `var` metadata to *y* */
    YNeedsVar,

    /** Would need `var` on a declaration that cannot be made `var`. */
    Impossible,
    ;

    companion object {
        fun doesYNeedVar(
            x: ResolvedName,
            y: ResolvedName,
            readsAndWrites: ReadsAndWrites,
            writesToXThatChangeToY: List<Write>,
            eliminatedY: (Write) -> Boolean,
        ): NeedsVar {
            val xDecl = readsAndWrites.declarations[x]?.soleElementOrNull
            val yDecl = readsAndWrites.declarations[y]?.soleElementOrNull
            val xParts = xDecl?.parts
            val yParts = yDecl?.parts
            return if (
                xParts != null && yParts != null &&
                varSymbol in xParts.metadataSymbolMultimap &&
                varSymbol !in yParts.metadataSymbolMultimap &&
                // If there is an assignment to x that would change that is upstream or downstream
                // of an extant write to y, then y would need to become var.
                wouldNeedVarPostRenameXToY(
                    x = x,
                    y = y,
                    writesToXThatChangeToY = writesToXThatChangeToY,
                    readsAndWrites = readsAndWrites,
                    eliminatedY = eliminatedY,
                )
            ) {
                if (canMakeVar(yParts, readsAndWrites)) {
                    YNeedsVar
                } else {
                    Impossible
                }
            } else {
                Ok
            }
        }

        /**
         * If one of the writes is down-stream of another, then it needs to be `var`.
         */
        fun wouldNeedVarPostRenameXToY(
            x: ResolvedName,
            y: ResolvedName,
            writesToXThatChangeToY: List<Write>,
            readsAndWrites: ReadsAndWrites,
            eliminatedY: (Write) -> Boolean,
        ): Boolean {
            for (writeToX in writesToXThatChangeToY) {
                val element = writeToX.containingPathElement ?: continue
                val liveWritesToY = readsAndWrites.writesLive(y, element)
                val liveWritesToX = readsAndWrites.writesLive(x, element)
                if (
                    liveWritesToY.any { !eliminatedY(it) } ||
                    liveWritesToX.any { it in writesToXThatChangeToY }
                ) {
                    return true
                }
            }
            val writesToY = readsAndWrites.writes[y] ?: emptyList()
            for (writeToY in writesToY) {
                if (eliminatedY(writeToY)) { continue }
                val element = writeToY.containingPathElement ?: continue
                val liveWritesToY = readsAndWrites.writesLive(y, element)
                val liveWritesToX = readsAndWrites.writesLive(x, element)
                if (
                    liveWritesToX.any { it in writesToXThatChangeToY } ||
                    liveWritesToY.any { !eliminatedY(it) }
                ) {
                    return true
                }
            }
            return false
        }

        fun stillHasWriteThatNeedsVar(
            name: ResolvedName,
            readsAndWrites: ReadsAndWrites,
        ) = readsAndWrites.writes[name]?.any { w ->
            val element = w.containingPathElement
            element != null && readsAndWrites.writesLive(name, element).isNotEmpty()
        } == true
    }
}

private fun typeMapFor(moduleRoot: BlockTree) = buildMap {
    val typeMap = this
    TreeVisit.startingAt(moduleRoot)
        .forEachContinuing {
            if (it is DeclTree) {
                val nameLeaf = it.parts?.name
                val name = nameLeaf?.content as? ResolvedName
                if (name != null) {
                    val type = nameLeaf.typeInferences?.type
                    typeMap[name] = if (name in typeMap) { null } else { type }
                }
            }
        }
        .visitPreOrder()
}

// Do not undo Weaver's pulling of specials rootwards.
private fun mustRemainAtStatementLevel(t: Tree): Boolean {
    if (t is CallTree) {
        val fn = t.childOrNull(0)?.functionContained
        when (fn) {
            BuiltinFuns.await,
            BuiltinFuns.handlerScope,
            BuiltinFuns.setLocalFn,
            BuiltinFuns.setpFn,
            -> return true
        }
    }
    return false
}

fun isGetStaticCall(t: Tree) = t is CallTree &&
    t.childOrNull(0)?.functionContained is GetStaticOp
