package lang.temper.frontend.rw

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.isHandlerScopeCall
import lang.temper.common.ForwardOrBack
import lang.temper.common.ZippedEntry
import lang.temper.common.buildListMultimap
import lang.temper.common.buildSetMultimap
import lang.temper.common.console
import lang.temper.common.forTwoMapsMutatingLeft
import lang.temper.common.isNotEmpty
import lang.temper.common.putMultiList
import lang.temper.common.putMultiSet
import lang.temper.common.soleElementOrNull
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.frontend.Module
import lang.temper.frontend.isNestedFunctionBody
import lang.temper.frontend.syntax.isAssignment
import lang.temper.interp.LongLivedUserFunction
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.ResolvedName
import lang.temper.type.NominalType
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.Document
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MaximalPath
import lang.temper.value.MaximalPathIndex
import lang.temper.value.MaximalPaths
import lang.temper.value.NameLeaf
import lang.temper.value.Preceder
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.TFunction
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.debug
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.isImplicits
import lang.temper.value.orderedPathIndices
import lang.temper.value.ssaSymbol
import lang.temper.value.toPseudoCode
import lang.temper.value.valueContained

private const val DEBUG = false
private val Document.debugging get() = DEBUG && !isImplicits
private inline fun Document.debug(body: () -> Unit) {
    if (debugging) {
        body()
    }
}

/**
 * Knowing which names are assigned and what is assigned to them.
 *
 * When `[123, name]`
 */
internal data class ReadsAndWrites(
    val paths: MaximalPaths,
    /**
     * The set of names that are used only in the current function or module body;
     * their liveness does not extend to nested or containing functions.
     */
    val localNames: Set<ResolvedName>,
    /** Names of any function/module inputs. */
    val inputNames: Set<ResolvedName>,
    /** Names of any function/module output. */
    val outputName: ResolvedName?,
    val declarations: Map<ResolvedName, List<DeclTree>>,
    val nestedFunctions: Map<ResolvedName?, List<NestedFunction>>,
    val reads: Map<ResolvedName, List<Read>>,
    val reifiedReads: Map<ResolvedName, List<ReifiedRead>>,
    val writes: Map<ResolvedName, List<Write>>,
    /**
     * [Write]s that are upstream of [Read]s.
     * In `{ x = f(); return x }`, there is a [Write]: `x = f()`.
     * That write is *upstream* of the [Read] of `x` in `return x`.
     */
    val upstream: Map<AbstractRead, Set<Write>>,
    /** [Read]s that are downstream of [Write]s */
    val downstream: Map<Write, Set<AbstractRead>>,
    val pathDiagnosticPositions: Map<MaximalPathIndex, Position>,
    val blockChildrenToPathElements: Map<BlockChildReference, MaximalPath.Element>,
    val declsByPathElement: Map<MaximalPath.Element, DeclTree>,
    val readsByPathElement: Map<MaximalPath.Element, List<AbstractRead>>,
    val writesByPathElement: Map<MaximalPath.Element, List<Write>>,
    val writesToInputParameters: Map<ResolvedName, Write>,
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("localNames", Hints.u) {
            arr {
                localNames.forEach { value("$it") }
            }
        }
        key("reads", Hints.u) { value(reads) }
        key("reifiedReads", Hints.u) { value(reifiedReads) }
        key("writes", Hints.u) { value(writes) }
        key("upstream", Hints.u) {
            obj {
                upstream.forEach { (name, writes) ->
                    key("$name") {
                        arr {
                            writes.forEach {
                                value(it)
                            }
                        }
                    }
                }
            }
        }
        key("downstream", Hints.u) { value(downstream) }
    }

    companion object {
        /**
         * Look at reads and writes case insensitively.
         * We want to know what is assigned to what.
         */
        fun forRoot(
            module: Module,
            root: BlockTree,
            inputParameters: List<DeclTree>,
            returnDecl: DeclTree?,
        ): ReadsAndWrites {
            val inputNames = buildSet {
                inputParameters.mapNotNullTo(this) { it.parts?.name?.content as ResolvedName? }
            }
            val outputName = returnDecl?.parts?.name?.content as ResolvedName?

            // Find declarations and distinguish names that are purely local
            // from those that are shared by multiple module/function bodies.
            val localNames = mutableSetOf<ResolvedName>()
            val declarations = mutableMapOf<ResolvedName, MutableList<DeclTree>>()
            val nestedFunctions = mutableMapOf<ResolvedName?, MutableList<NestedFunction>>()

            fun canonicalNameForFun(funTree: FunTree): ResolvedName? {
                val edge = funTree.incoming
                val parent = edge?.source
                if (parent != null && parent.edgeOrNull(2) == edge && isAssignment(parent)) {
                    val left = parent.child(1)
                    val name = (left as? LeftNameLeaf)?.content as ResolvedName?
                    if (
                        name != null &&
                        // Sole initializer for a const declaration makes name the canonical fn name.
                        declarations[name]?.soleElementOrNull?.parts
                            ?.metadataSymbolMultimap?.contains(ssaSymbol) == true
                    ) {
                        return name
                    }
                }
                return null
            }

            // Scan the tree and build up the collections above
            run {
                val declaredLocally = mutableSetOf<ResolvedName>()
                val usedExternally = mutableSetOf<ResolvedName>()
                fun visit(t: Tree, local: Boolean) {
                    if (t is NameLeaf) {
                        val name = t.content as ResolvedName
                        if (!local) {
                            usedExternally.add(name)
                        }
                    } else if (t is DeclTree) {
                        val name = t.parts?.name?.content as ResolvedName?
                        if (name != null) {
                            if (local) {
                                declaredLocally.add(name)
                            } else {
                                usedExternally.add(name)
                            }
                            declarations.putMultiList(name, t)
                        }
                    }
                    t.children.forEach { child ->
                        visit(child, local = local && t !is FunTree)
                    }
                }
                visit(root, local = true)
                inputParameters.forEach { visit(it, local = true) }
                returnDecl?.let {
                    // Module body return declarations will be children
                    // of the body, so already visited, but function body
                    // return declarations will be children of the FunTree.
                    if (it.incoming?.source != root) {
                        visit(it, local = true)
                    }
                }

                for (name in declaredLocally) {
                    if (name !in usedExternally) {
                        localNames.add(name)
                    }
                }
            }

            val maximalPaths = forwardMaximalPaths(root, assumeFailureCanHappen = true)
            root.document.debug {
                maximalPaths.debug(console, root)
            }

            val pathDiagnosticPositions = buildMap {
                // Store some information so that we can generate error messages
                // with position information about paths like
                // "variable x not assigned along paths at file.temper:50"
                this[MaximalPathIndex.beforeEntry] = root.pos.leftEdge
                maximalPaths.maximalPaths.forEach { path ->
                    this[path.pathIndex] = path.diagnosticPosition
                }
            }

            // First find reads and writes.
            // Then re-walk paths flow-sensitively,
            // keeping a list of which assignments are live
            // to find upstream/downstream relationships between
            // reads and writes.
            val reads = mutableMapOf<ResolvedName, MutableList<Read>>()
            val reifiedReads = mutableMapOf<ResolvedName, MutableList<ReifiedRead>>()
            val writes = mutableMapOf<ResolvedName, MutableList<Write>>()
            // For function bodies, assume the arguments are written before the
            // body starts and any return declaration is written
            // after the body exits.
            // Doing this insulates us from confusion around a `var` function input
            // that is written in the function body.
            val readOfReturn = returnDecl?.parts?.name?.content?.let { returnName ->
                check(returnName is ResolvedName)
                val read = Read(returnName, null, null, module.lineFor(root.pos.rightEdge))
                reads.putMultiList(returnName, read)
                read
            }
            val writesToInputParameters = inputParameters.mapNotNull { parameterDecl ->
                parameterDecl.parts?.name?.let { parameterNameLeaf ->
                    val parameterName = parameterNameLeaf.content
                    check(parameterName is ResolvedName)
                    val write = Write(
                        name = parameterName,
                        containingPathElement = null,
                        tree = parameterDecl,
                        writeKind = WriteKind.Other,
                        assigned = null,
                        lineNo = module.lineFor(parameterNameLeaf.pos),
                    )
                    writes.putMultiList(parameterName, write)
                    write
                }
            }

            val blockChildrenToPathElements =
                mutableMapOf<BlockChildReference, MaximalPath.Element>()
            for (path in maximalPaths.maximalPaths) {
                fun leftNameFor(t: Tree?) = (t as? NameLeaf)?.content as ResolvedName?
                fun readAt(element: MaximalPath.Element, t: Tree?): AbstractRead? {
                    when (t) {
                        is RightNameLeaf -> {
                            val name = t.content as ResolvedName
                            return Read(name, t, element, lineNo = module.lineFor(t))
                        }

                        is ValueLeaf -> {
                            val value = t.valueContained
                            val reifiedType = TType.unpackOrNull(value)
                            if (reifiedType != null) {
                                val type = (reifiedType as? ReifiedType)?.type
                                if (type is NominalType) {
                                    val name = type.definition.name
                                    return ReifiedRead(name, t, element, lineNo = module.lineFor(t))
                                }
                            } else {
                                val fn = TFunction.unpackOrNull(value)
                                if (fn is LongLivedUserFunction) {
                                    val stay = fn.stayLeaf
                                    val funTree = stay.incoming?.source as? FunTree
                                    if (funTree != null) {
                                        val name = canonicalNameForFun(funTree)
                                        if (name != null) {
                                            return ReifiedRead(name, t, element, lineNo = module.lineFor(t))
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                    return null
                }

                fun visit(element: MaximalPath.Element) {
                    val ref = element.ref
                    blockChildrenToPathElements[ref] = element
                    val tree = root.dereference(ref)?.target ?: return
                    // Look for writes in tree.
                    // There are a couple different kinds of writes:
                    // - `a = b`
                    //   A simple assignment
                    // - `a = hs(fail, b)`
                    //   An assignment guarded by hs() that also writes a fail bit
                    //   that tells us whether the assigned value is usable.
                    // - `hs(fail, a = b)`
                    //   An assignment that may fail in the interpreter due to a type error.
                    //   This will either be found as safe during type checking in a late stage
                    //   or turned into an error node, but we need to deal with it now.
                    // - `setp(propertyName, b)`
                    //   A non-assignment to a backed property from within a method
                    //   of the declaring type.

                    // Recurse to find writes looking through `=` and `hs` calls.
                    // Assume the weaver pulled other assignments to the root.
                    fun findWrites(t: Tree, nested: Boolean): Tree? {
                        if (t !is CallTree || !writesArgument1(t)) { return null }
                        val name = leftNameFor(t.childOrNull(1)) ?: return null

                        val isAssignment = isAssignment(t) // Is a simple `=`, not, for example, an `hs` call.
                        val isHsCall = !isAssignment && isHandlerScopeCall(t)

                        val rightTree: Tree? = if (isAssignment || isHsCall) {
                            t.childOrNull(2)?.let { child ->
                                findWrites(child, nested = true) ?: child
                            }
                        } else {
                            null
                        }

                        val writeKind = when {
                            isAssignment && nested -> WriteKind.NestedAssignment
                            isAssignment -> WriteKind.SimpleAssignment
                            else -> WriteKind.Other
                        }
                        root.document.debug {
                            console.log("Found write ${t.toPseudoCode(singleLine = true)}")
                        }
                        writes.putMultiList(
                            name,
                            Write(
                                name = name,
                                containingPathElement = element,
                                tree = t,
                                writeKind = writeKind,
                                assigned = if (isAssignment) {
                                    readAt(element, t.childOrNull(2))
                                } else {
                                    null
                                },
                                lineNo = module.lineFor(t),
                            ),
                        )

                        return rightTree
                    }
                    root.document.debug {
                        console.log("Looking for writes in ${tree.toPseudoCode()}")
                    }
                    findWrites(tree, nested = false)

                    TreeVisit.startingAt(tree)
                        .forEach { t ->
                            when (val read = readAt(element, t)) {
                                is Read -> reads.putMultiList(read.name, read)
                                is ReifiedRead -> reifiedReads.putMultiList(read.name, read)
                                null -> {}
                            }

                            if (t is FunTree) {
                                val fnName = canonicalNameForFun(t)
                                nestedFunctions.putMultiList(
                                    fnName,
                                    NestedFunction(fnName, t, element, lineNo = module.lineFor(t)),
                                )
                            }

                            if (isNestedFunctionBody(t)) {
                                VisitCue.SkipOne
                            } else {
                                VisitCue.Continue
                            }
                        }
                        .visitPreOrder()
                }

                path.elements.forEach { visit(it) }
                path.followers.forEach { (condition) ->
                    if (condition != null) { visit(condition) }
                }
            }

            val readsByPathElement = buildListMultimap {
                for (readMap in listOf(reads, reifiedReads)) {
                    readMap.values.forEach { readList ->
                        readList.forEach { read ->
                            val el = read.containingPathElement
                            if (el != null) {
                                this.putMultiList(el, read)
                            }
                        }
                    }
                }
            }
            val writesByPathElement = buildListMultimap {
                writes.values.forEach { writesToSameName ->
                    writesToSameName.forEach { write ->
                        val element = write.containingPathElement
                        if (element != null) {
                            this.putMultiList(element, write)
                        }
                    }
                }
            }
            val declsByPathElement = buildMap {
                declarations.values.forEach { declsForSameName ->
                    declsForSameName.forEach { decl ->
                        val edge = decl.incoming
                        if (edge?.source == root) {
                            val ref = BlockChildReference(edge.edgeIndex, decl.pos)
                            blockChildrenToPathElements[ref]?.let { pathElement ->
                                this[pathElement] = decl
                            }
                        }
                    }
                }
            }

            // To propagate liveness info, we need to know how it changes for each path.
            // We can use that to figure out what is live at the start of each path by
            val livenessDeltas = maximalPaths.pathIndices.map { pathIndex ->
                val path = maximalPaths[pathIndex]
                val delta = mutableMapOf<ResolvedName, CoverageDelta>()

                for (element in path.elements) {
                    val decl = declsByPathElement[element]
                    if (decl != null) {
                        val declaredName = decl.parts?.name?.content as ResolvedName?
                        if (declaredName != null) {
                            delta[declaredName] = CoverageDelta.Declared
                        }
                    } else {
                        writesByPathElement[element]?.forEach { write ->
                            delta[write.name] = CoverageDelta.Local(write)
                        }
                    }
                }
                // Check that we're not missing anything by skipping follower conditions.
                for (follower in path.followers) {
                    val condition = follower.condition ?: continue
                    if (
                        declsByPathElement[condition] != null ||
                        !writesByPathElement[condition].isNullOrEmpty()
                    ) {
                        error("${root.dereference(condition.ref)?.target?.toPseudoCode()}")
                    }
                }
                delta
            }
            check(livenessDeltas.size == maximalPaths.pathIndices.endExclusive.index)

            // Lots of temporary variables are used briefly and then never used again, in
            // one pass only.
            // We can avoid a lot of work in propagating write liveness if we
            // don't bother propagating write-liveness for variables to where they
            // are never used.
            // This map relates path indices to sets of names of variables that are
            // used in that path or in followers.
            val namesUsedInOrAfter = mutableMapOf<MaximalPathIndex, MutableSet<ResolvedName>>()
            run {
                val queue = ArrayDeque<MaximalPathIndex>()
                // Process in an order where followers come first so that they're available.
                queue.addAll(orderedPathIndices(maximalPaths, ForwardOrBack.Forward))
                val onQueue = mutableSetOf<MaximalPathIndex>()
                onQueue.addAll(queue)
                while (true) {
                    val pathIndex = queue.removeFirstOrNull() ?: break
                    onQueue.remove(pathIndex)
                    val path = maximalPaths[pathIndex]

                    var usedSet = namesUsedInOrAfter[pathIndex]
                    val sizeBefore = usedSet?.size ?: 0
                    if (usedSet == null) {
                        usedSet = mutableSetOf()
                        fun addUsesToSet(element: MaximalPath.Element?) {
                            declsByPathElement[element]?.let {
                                val name = it.parts?.name?.content as ResolvedName?
                                if (name != null) { usedSet.add(name) }
                            }
                            readsByPathElement[element]?.forEach {
                                usedSet.add(it.name)
                            }
                            writesByPathElement[element]?.forEach {
                                usedSet.add(it.name)
                            }
                        }
                        path.elements.forEach { addUsesToSet(it) }
                        path.followers.forEach { (condition) ->
                            addUsesToSet(condition)
                        }
                        // Treat the result as being read by the caller.
                        if (pathIndex in maximalPaths.exitPathIndices && readOfReturn != null) {
                            usedSet.add(readOfReturn.name)
                        }
                        namesUsedInOrAfter[pathIndex] = usedSet
                    }
                    for (follower in path.followers) {
                        val followerIndex = follower.pathIndex ?: continue
                        namesUsedInOrAfter[followerIndex]?.let { usedSet.addAll(it) }
                    }

                    if (usedSet.size > sizeBefore) {
                        for ((precederIndex) in path.preceders) {
                            if (precederIndex !in onQueue) {
                                onQueue.add(precederIndex)
                                queue.add(precederIndex)
                            }
                        }
                    }
                }
            }
            // Second, figure out, at the start of each path, which writes are live.
            // This might require visiting paths multiple times, so this iterates to convergence.
            // Then walk each path once more with the live writes and build upstream/downstream
            // relationships.
            val writesLiveAtStart = mutableMapOf<MaximalPathIndex, Map<ResolvedName, Set<Write>>>()
            run {
                val atEnd = mutableMapOf<MaximalPathIndex, Map<ResolvedName, Set<Write>>>()

                atEnd[MaximalPathIndex.beforeEntry] = writesToInputParameters.associate {
                    it.name to setOf(it)
                }

                val queue = ArrayDeque<MaximalPathIndex>()
                // Process in an order where preceders come first so that they're available.
                queue.addAll(orderedPathIndices(maximalPaths, ForwardOrBack.Back))
                val onQueue = mutableSetOf<MaximalPathIndex>()
                onQueue.addAll(queue)
                while (true) {
                    val pathIndex = queue.removeFirstOrNull() ?: break
                    onQueue.remove(pathIndex)

                    val path = maximalPaths[pathIndex]

                    var preceders = path.preceders
                    if (pathIndex == maximalPaths.entryPathIndex) {
                        preceders = preceders +
                            listOf(Preceder(MaximalPathIndex.beforeEntry, dir = ForwardOrBack.Forward))
                    }

                    val atEndInProgress = mutableMapOf<ResolvedName, Set<Write>>()
                    val namesReadInOrAfterPath = namesUsedInOrAfter[pathIndex] ?: emptySet()
                    for (preceder in preceders) {
                        val atEndOfP = atEnd[preceder.pathIndex] ?: emptyMap()
                        forTwoMapsMutatingLeft(atEndInProgress, atEndOfP) { e ->
                            if (e.key !in namesReadInOrAfterPath) { return@forTwoMapsMutatingLeft }
                            atEndInProgress[e.key] = when (e) {
                                is ZippedEntry.Both -> e.left + e.right
                                is ZippedEntry.LeftOnly -> e.left
                                is ZippedEntry.RightOnly -> e.right
                            }
                        }
                    }
                    writesLiveAtStart[pathIndex] = atEndInProgress.toMap()

                    for ((name, delta) in livenessDeltas[pathIndex.index]) {
                        when (delta) {
                            CoverageDelta.Declared -> atEndInProgress.remove(name)
                            is CoverageDelta.Local -> atEndInProgress[name] = setOf(delta.write)
                        }
                    }

                    val newAtEnd = atEndInProgress.toMap()
                    val prevAtEnd = atEnd[pathIndex]
                    val changed = (prevAtEnd ?: emptyMap()) != newAtEnd
                    if (changed || prevAtEnd == null) {
                        atEnd[pathIndex] = newAtEnd
                    }
                    if (changed) {
                        path.followers.forEach { (_, followerPathIndex) ->
                            if (followerPathIndex != null && followerPathIndex !in onQueue) {
                                queue.add(followerPathIndex)
                                onQueue.add(followerPathIndex)
                            }
                        }
                    }
                }
            }

            root.document.debug {
                console.group("liveAtStart") {
                    for ((pathIndex, nameToCoverage) in writesLiveAtStart) {
                        console.group("path $pathIndex") {
                            for ((name, writesForName) in nameToCoverage) {
                                console.log("$name -> $writesForName")
                            }
                        }
                    }
                }
            }

            // We need to propagate writes across CFG paths.
            // There might be multiple incoming branches that join at
            // the start of the path so multiple writes may be live
            // for the same name, but when we're propagating writes
            // along a path, and we see a name written or a declaration
            // we clobber all of them, because paths internally are linear.
            fun maskPreviousWritesToSameName(
                el: MaximalPath.Element,
                writesLive: MutableMap<ResolvedName, Set<Write>>,
            ) {
                val decl = declsByPathElement[el]
                if (decl != null) {
                    val name = (decl.parts?.name?.content ?: return)
                        as ResolvedName
                    writesLive.remove(name)
                } else {
                    writesByPathElement[el]?.forEach { write ->
                        writesLive[write.name] = setOf(write)
                    }
                }
            }

            // Now, walk each path one more time to build a list of writes that are
            // upstream of each read.
            // Then reverse that map to get the downstream map.
            val upstream = buildMap {
                val liveAtEnd = mutableMapOf<MaximalPathIndex, Map<ResolvedName, Set<Write>>>()
                for (pathIndex in maximalPaths.pathIndices) {
                    val path = maximalPaths[pathIndex]
                    val live = writesLiveAtStart.getValue(pathIndex).toMutableMap()

                    // Walk the path again propagating liveness but also populating upstream
                    fun considerPathElement(el: MaximalPath.Element?) {
                        if (el == null) {
                            return
                        }
                        readsByPathElement[el]?.forEach { read ->
                            live[read.name]?.let {
                                this[read] = it
                            }
                        }
                        maskPreviousWritesToSameName(el, live)
                    }
                    path.elements.forEach { considerPathElement(it) }
                    path.followers.forEach { (cond) ->
                        considerPathElement(cond)
                    }
                    liveAtEnd[pathIndex] = live
                }
                // Finally, the read of the return value is downstream of any
                // writes to it that are visible at the end.
                if (readOfReturn != null) {
                    maximalPaths.exitPathIndices.forEach { exitPathIndex ->
                        liveAtEnd.getValue(exitPathIndex)[readOfReturn.name]
                            ?.let { writeToReturn ->
                                this[readOfReturn] = writeToReturn
                            }
                    }
                }
            }

            val downstream = buildSetMultimap {
                upstream.forEach { (read, writes) ->
                    writes.forEach { write ->
                        this.putMultiSet(write, read)
                    }
                }
            }

            return ReadsAndWrites(
                paths = maximalPaths,
                localNames = localNames.toSet(),
                inputNames = inputNames,
                outputName = outputName,
                declarations = declarations.mapValues { it.value.toList() },
                nestedFunctions = nestedFunctions.mapValues { it.value.toList() },
                reads = reads.mapValues { it.value.toList() },
                reifiedReads = reifiedReads.mapValues { it.value.toList() },
                writes = writes.mapValues { it.value.toList() },
                upstream = upstream,
                downstream = downstream,
                pathDiagnosticPositions = pathDiagnosticPositions,
                blockChildrenToPathElements = blockChildrenToPathElements.toMap(),
                declsByPathElement = declsByPathElement,
                readsByPathElement = readsByPathElement,
                writesByPathElement = writesByPathElement,
                writesToInputParameters = writesToInputParameters.associateBy { it.name },
            )
        }

        val zeroValue: ReadsAndWrites = ReadsAndWrites(
            paths = MaximalPaths.zeroValue,
            localNames = emptySet(),
            inputNames = emptySet(),
            outputName = null,
            declarations = emptyMap(),
            nestedFunctions = emptyMap(),
            reads = emptyMap(),
            reifiedReads = emptyMap(),
            writes = emptyMap(),
            upstream = emptyMap(),
            downstream = emptyMap(),
            pathDiagnosticPositions = emptyMap(),
            blockChildrenToPathElements = emptyMap(),
            declsByPathElement = emptyMap(),
            readsByPathElement = emptyMap(),
            writesByPathElement = emptyMap(),
            writesToInputParameters = emptyMap(),
        )
    }
}

internal sealed class AbstractRead(
    val name: ResolvedName,
    val containingPathElement: MaximalPath.Element?,
    /** Useful for testing and debugging */
    val lineNo: Int,
) : Structured {
    abstract val tree: Tree?
    val pos: Position? get() = tree?.pos

    override fun destructure(structureSink: StructureSink) {
        structureSink.value("$this")
    }
    abstract override fun toString(): String
}

/** A read of a name */
internal class Read(
    name: ResolvedName,
    override val tree: NameLeaf?,
    containingPathElement: MaximalPath.Element?,
    lineNo: Int,
) : AbstractRead(name, containingPathElement, lineNo) {
    override fun hashCode(): Int = tree.hashCode()
    override fun equals(other: Any?) =
        this === other || (
            other is Read && this.name == other.name && this.tree == other.tree
            )
    override fun toString(): String = "R($name @ L$lineNo)"
}

/**
 * A reference to something with a canonical name via a value stored in a
 * [ValueLeaf] including reified types and function references.
 */
internal class ReifiedRead(
    name: ResolvedName,
    override val tree: ValueLeaf?,
    containingPathElement: MaximalPath.Element?,
    lineNo: Int,
) : AbstractRead(name, containingPathElement, lineNo) {
    override fun hashCode(): Int = tree.hashCode()
    override fun equals(other: Any?) =
        this === other || (
            other is ReifiedRead && this.name == other.name && this.tree == other.tree
            )

    override fun toString(): String = "RR($name @ L$lineNo)"
}

internal enum class WriteKind {
    SimpleAssignment,
    NestedAssignment,
    Other,
    ;

    val isAssignment get() = when (this) {
        SimpleAssignment,
        NestedAssignment,
        -> true
        Other -> false
    }
}

internal class Write(
    val name: ResolvedName,
    val containingPathElement: MaximalPath.Element?,
    val tree: Tree?,
    val writeKind: WriteKind,
    val assigned: AbstractRead?,
    /** Useful for testing and debugging */
    private val lineNo: Int,
) : Structured {
    override fun destructure(structureSink: StructureSink) {
        structureSink.value("$this")
    }

    override fun hashCode(): Int = tree.hashCode() + 31 * name.hashCode()
    override fun equals(other: Any?) =
        this === other || (
            other is Write && this.tree == other.tree && this.name == other.name &&
                this.writeKind == other.writeKind && this.assigned == other.assigned
            )

    override fun toString(): String = buildString {
        append("W(")
        append(name)
        append(' ')
        when (writeKind) {
            WriteKind.SimpleAssignment -> append('A')
            WriteKind.NestedAssignment -> append("nA")
            WriteKind.Other -> append('O')
        }
        append(" @ L")
        append(lineNo)
        append(")")
    }

    @Suppress("unused") // For debugging
    val abbreviatedDiagnostic get() = buildString {
        append('L')
        append(lineNo)
        when (writeKind) {
            WriteKind.SimpleAssignment -> {}
            WriteKind.NestedAssignment -> append("/N")
            WriteKind.Other -> append("/O")
        }
    }
}

/** Information about a function that appears nested inside another. */
internal class NestedFunction(
    val name: ResolvedName?,
    val tree: FunTree,
    val containingPathElement: MaximalPath.Element,
    private val lineNo: Int,
) : Structured {
    override fun destructure(structureSink: StructureSink) {
        structureSink.value("$this")
    }

    override fun hashCode(): Int = (name?.hashCode() ?: 0) + 31 * tree.hashCode()
    override fun equals(other: Any?) =
        other is NestedFunction && this.name == other.name &&
            this.tree == other.tree

    override fun toString(): String = buildString {
        append("(F")
        if (name != null) {
            append(' ')
            append(name)
        }
        append(" @ ")
        append(lineNo)
        append(')')
    }
}

internal fun Module.lineFor(p: Positioned): Int {
    val pos = p.pos
    val diagnosticPath = (pos.loc as? FileRelatedCodeLocation)?.sourceFile
    val filePositions = this.filePositions[diagnosticPath]
    return filePositions?.filePositionAtOffset(pos.left)?.line ?: 0
}

private fun writesArgument1(call: CallTree): Boolean {
    val callee = call.childOrNull(0)?.valueContained(TFunction)
    return callee?.assignsArgumentOne == true
}

private sealed class CoverageDelta {
    object Declared : CoverageDelta()
    data class Local(val write: Write) : CoverageDelta()
}

/**
 * Which writes with name[name] are live at [location].
 */
internal fun ReadsAndWrites.writesLive(
    name: ResolvedName,
    /**
     * Must be an element from [ReadsAndWrites.paths].
     */
    location: MaximalPath.Element,
): Iterable<Write> {
    val readsAndWrites = this
    return object : Iterable<Write> {
        override fun iterator(): Iterator<Write> {
            val extras = (writesToInputParameters[name]?.let { listOf(it) } ?: listOf()).iterator()
            return LiveIterator(readsAndWrites, location, extras) liveWrites@{ element ->
                if (element === location) {
                    // Writes are not live until after they complete
                    return@liveWrites emptyList<Write>().iterator()
                }
                val decl = readsAndWrites.declsByPathElement[element]
                if (decl != null && decl.parts?.name?.content == name) {
                    // Stop traversal when we get back to the declaration.
                    // This is an important performance optimization because
                    // it prevents walking the entirety of a large CFG for
                    // tightly scoped variables.
                    return@liveWrites emptyList<Write>().iterator()
                }
                val writes = readsAndWrites.writesByPathElement[element]
                if (writes.isNullOrEmpty()) {
                    return@liveWrites null
                }
                val indexOfFirst = writes.indexOfFirst { it.name == name }
                if (indexOfFirst < 0) { return@liveWrites null }
                writes.subList(indexOfFirst, writes.size)
                    .stream()
                    .filter { it.name == name }
                    .iterator()
            }
        }
    }
}

private class LiveIterator<T : Any>(
    val readsAndWrites: ReadsAndWrites,
    val start: MaximalPath.Element,
    val extras: Iterator<T>,
    /** Non-null output emits the found items and stops following the containing path. */
    val elementToItems: (MaximalPath.Element) -> Iterator<T>?,
) : Iterator<T> {
    override fun hasNext(): Boolean = findPending() != null
    override fun next(): T {
        val result = findPending() ?: throw NoSuchElementException()
        pending = null
        return result
    }

    private var pending: T? = null
    private var pathIterators = ArrayDeque<PathIterator>()
    private val pathsIterated = mutableSetOf<MaximalPathIndex>()
    private var itemIterator: Iterator<T>? = null

    init {
        val firstPathIterator = PathIterator(readsAndWrites.paths[start.pathIndex])
        while (firstPathIterator.next() != start) {
            // Skip over items at or following start.
        }
        pathIterators.add(firstPathIterator)
        // We intentionally do not add firstPathIterator.path.pathIndex to
        // pathsIterated.  If we did, then we would never enumerate
        // elements after start when start is reachable by virtue
        // of being part of a loop.
        // Instead, in findPending we auto check if we've re-reached
        // start and abort the re-entered path, which must now be on
        // pathsIterated.
    }

    private fun findPending(): T? {
        if (pending != null) { return pending }

        // We need an outer loop so that we can reuse the code that extracts
        // a pending item from the item iterator if we have one, but also
        // if we find a new item iterator.
        itemPathIteratorLoop@
        while (true) {
            val itemIterator = this.itemIterator
            if (itemIterator != null) {
                if (itemIterator.hasNext()) {
                    pending = itemIterator.next()
                    return pending
                }
                this.itemIterator = null
            }

            pathIteratorsLoop@
            while (pathIterators.isNotEmpty()) {
                val pathIterator = pathIterators.removeFirst()
                while (pathIterator.hasNext()) {
                    val element = pathIterator.next()
                    if (element == start) {
                        // See note in init above.
                        continue@pathIteratorsLoop
                    }
                    val newItemIterator = elementToItems(element)
                    if (newItemIterator != null) {
                        this.itemIterator = newItemIterator
                        // The items in newItemIterator block any liveness
                        // from preceding items on the current pathIterator,
                        // so we don't bother with any more elements on this
                        // path and do not enqueue preceders.
                        continue@itemPathIteratorLoop
                    }
                }
                // Nothing found on this path, so go to preceders breadth-first.
                for ((precederIndex) in pathIterator.path.preceders) {
                    if (precederIndex !in pathsIterated) {
                        pathsIterated.add(precederIndex)
                        pathIterators.add(PathIterator(readsAndWrites.paths[precederIndex]))
                    }
                }
            }
            // Found no items on the item iterator and found no new item iterator
            if (extras.hasNext()) {
                pending = extras.next()
            }
            return pending
        }
    }
}

private class PathIterator(val path: MaximalPath) : Iterator<MaximalPath.Element> {
    override fun hasNext(): Boolean = peek() != null

    override fun next(): MaximalPath.Element {
        val result = peek() ?: throw NoSuchElementException()
        if (followerIndex >= 0) {
            followerIndex -= 1
        } else {
            check(elementIndex >= 0)
            elementIndex -= 1
        }
        return result
    }

    private var followerIndex = path.followers.lastIndex
    private var elementIndex = path.elements.lastIndex

    fun peek(): MaximalPath.Element? {
        while (followerIndex >= 0) {
            val condition = path.followers[followerIndex].condition
            if (condition == null) {
                followerIndex -= 1
            } else {
                return condition
            }
        }
        return path.elements.getOrNull(elementIndex)
    }

    override fun toString(): String =
        "PathIterator(path${path.pathIndex}, follower=$followerIndex, element=$elementIndex)"
}
