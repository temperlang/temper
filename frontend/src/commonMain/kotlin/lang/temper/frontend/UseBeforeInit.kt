package lang.temper.frontend

import lang.temper.builtin.Types
import lang.temper.common.Console
import lang.temper.common.ForwardOrBack
import lang.temper.common.Log
import lang.temper.common.ZippedEntry
import lang.temper.common.console
import lang.temper.common.forTwoMapsMutatingLeft
import lang.temper.common.ignore
import lang.temper.common.isNotEmpty
import lang.temper.common.mapFirst
import lang.temper.common.putMultiSet
import lang.temper.frontend.rw.NestedFunction
import lang.temper.frontend.rw.ReadsAndWrites
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.InternalModularName
import lang.temper.name.ResolvedName
import lang.temper.name.Temporary
import lang.temper.type.isVoidLike
import lang.temper.value.BlockTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.DeclTree
import lang.temper.value.ErrorFn
import lang.temper.value.MaximalPath
import lang.temper.value.MaximalPathIndex
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TEdge
import lang.temper.value.TProblem
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.debug
import lang.temper.value.errorFn
import lang.temper.value.fromTypeSymbol
import lang.temper.value.isImplicits
import lang.temper.value.orderedPathIndices
import lang.temper.value.toPseudoCode
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeDefinitionAtLeafOrNull
import lang.temper.value.typeFromSignature
import lang.temper.value.void

private const val DEBUG = false

/**
 * Replaces uses of a variable name before it's initialized
 * with an [ErrorFn] node.
 *
 * We need to recognize uses of names before they are initialized.
 *
 *     let x;  // Declaration of x
 *     console.log(x);  // ERROR: x has not yet been initialized
 *     x = f();
 *
 * It may be the case that a name is declared in time, but only along some
 * control-flow paths and not others.
 *
 *     let x;
 *     if (randomBool()) {
 *       x = f();
 *     } /* else { } */
 *     console.log(x); // ERROR: x not initialized along implicit else path.
 *
 * We also need to recognize uses in a function.
 * Sometimes a function is lexically defined before a variable, but uses
 * of a variable in a function body (or default parameter expressions)
 * do not matter until the function is called.
 *
 *     let x;
 *     let g() { console.log(x); }
 *     x = f();  // OK.  g not called until later.
 *     g();
 *
 * This is important for co-recursive functions.  Co-recursive functions
 * cannot be re-ordered to allow only backwards reference because each
 * refers to the other.
 *
 * But functions can be called in problematic ways:
 *
 *     let x;
 *     let g() { console.log(x); }
 *     g(); // ERROR: g() uses x which hasn't been initialized before this call
 *     x = f();
 *
 * Besides function calls, constructing values of locally defined types can
 * also be out of order.
 *
 *     let x;
 *     interface I {
 *       public method(): String { x }
 *     }
 *     class C extends I {}
 *     let c = new C();  // ERROR: C.method, inherited from I, needs x.
 *     x = f();
 *
 * If those last two statements were reordered, there would be no problem.
 *
 * This pass is meant to happen after the
 * [define stage][lang.temper.stage.Stage.Define]
 * and after [weaving][Weaver] so that:
 *
 * - [parsed names][lang.temper.name.ParsedName] have been resolved to
 *   [ResolvedName]s
 * - type members are available as declarations
 * - property bags have been resolved to `new` of known types
 * - control flow macros like `if` have been inlined into
 *   block's control-flow graphs
 * - calls to special functions, including assignments,
 *   have been pulled to statement level
 * - parameter default expressions have been moved into their function body so
 *   uses of variables in them can be handled along with the rest of the body
 *
 * To that end, we divide analysis into several stages:
 *
 * 1. Find local type definitions and group their members.
 * 2. Process the module and function bodies as below.
 *
 * For each body, we:
 *
 * 1. identify its externally declared inputs and return variables,
 * 2. find its [ReadsAndWrites] and references to reified types in
 *   [value leaves][ValueLeaf] that may be used in `new` applications,
 * 3. look for uses of local variable before initialization by using the
 *   write liveness info in [ReadsAndWrites],
 * 4. consider reads of named functions and named types.  Group them,
 *   recurse to bodies if not yet used, and check that their free variables
 *   are live at the use.
 * 5. store a list of its free variables that need to be defined before
 *   it is invoked.
 *
 * Per 4. above, we might analyse a function body on demand.
 * We default to post-order analysis of nested functions.  In the case where
 * a function is not named, for example in `foo(x) { ... }`, we need to
 * make worst case assumptions about the block lambda: it might be called
 * immediately, multiple, or never.  We require that its free variables have
 * been initialized before the call to `foo` that receives it starts.
 */
internal class UseBeforeInit(
    private val module: Module,
    private val moduleRoot: BlockTree,
    /** Needs to be written at the end. */
    private val moduleOutputName: ResolvedName?,
) {
    fun check() {
        debug {
            group("Before UseBeforeInit") {
                moduleRoot.toPseudoCode(textOutput)
            }
        }

        val usesBeforeInit = scanForUsesBeforeInit()
        fixupTreeAndReportProblems(usesBeforeInit)
    }

    private fun scanForUsesBeforeInit(): List<Pair<TEdge, LogEntry?>> = buildList {
        // We collect a list of problems and later use them to mutate the tree.
        val usesBeforeInit = this

        val freePerFn = mutableMapOf<NestedFunction, FreeNames>()
        val locallyDeclaredTypes = buildMap {
            moduleRoot.children.forEach { tree ->
                if (tree is DeclTree) {
                    val metadata = tree.parts?.metadataSymbolMap ?: emptyMap()
                    val t = metadata[typeDeclSymbol]
                        ?: metadata[typeDefinedSymbol]
                        ?: metadata[fromTypeSymbol]
                    if (t != null) {
                        val definition = t.target.typeDefinitionAtLeafOrNull
                        if (definition != null) {
                            this[definition.name] = definition
                        }
                    }
                }
            }
        }
        debug {
            group("locallyDeclaredTypes") {
                locallyDeclaredTypes.forEach {
                    log("${it.key}")
                }
            }
        }

        fun processBody(
            inputParameters: List<DeclTree>,
            returnDecl: DeclTree?,
            root: BlockTree,
        ): FreeNames {
            debug {
                group("Processing body") {
                    root.toPseudoCode(textOutput, detail = PseudoCodeDetail(elideFunctionBodies = true))
                }
            }

            val readsAndWrites =
                ReadsAndWrites.forRoot(module, root, inputParameters, returnDecl)
            val paths = readsAndWrites.paths
            debug {
                paths.debug(this, root)
            }

            val freeNames = mutableMapOf<ResolvedName, MutableSet<Position>>()

            // Post-order recurse to nested functions
            for (fns in readsAndWrites.nestedFunctions.values) {
                for (fn in fns) {
                    if (fn !in freePerFn) {
                        val parts = fn.tree.parts
                        val fnInputParameters = parts?.formals ?: emptyList()
                        val fnReturnDecl = parts?.returnDecl
                        val fnBody = parts?.body as BlockTree

                        val freeForFn = processBody(
                            inputParameters = fnInputParameters,
                            returnDecl = fnReturnDecl,
                            root = fnBody,
                        )
                        freePerFn[fn] = freeForFn
                    }
                }
            }

            // Missing coverage sets track whether live writes with the same name
            // cover all paths of control that reach the covered point.
            //
            //      // x is fully covered in this case.
            //      let x;
            //      // Here we branch along two paths
            //      if (randomBool()) {
            //        x = 1;
            //      } else {
            //        x = 2;
            //      } // Here those two paths join
            //      f(x); // LEGAL since x was initialized along all paths before use
            //
            //  But if there is a path that doesn't write `x`, then it's not legal
            //  to use `x`.
            //
            //      // x is not fully covered in this case.
            //      let x;
            //      // Again, branch along two paths.
            //      if (randomBool()) {
            //        x = 1;
            //      } // There's an implicit `else` that does not assign `x`.
            //      f(x); // ILLEGAL `x` was not assigned a value when the die rolled false.
            //
            //  We would not want to mask errors in programs by renaming `x` to `y` in the
            //  below.
            //
            //      let x;
            //      let y = 1;
            //      if (randomBool()) {
            //        x = y;
            //      }
            //      f(x); // ERROR: `x` assigned only half the time.
            //
            //  If we were to rewrite `x` to `y` we would get the valid program below, but
            //  in so doing, mask a programmer error that might reappear in a new version
            //  of the compiler.
            //
            //      let y = 1;
            //      if (randomBool()) {
            //        ; // no-op
            //      }
            //      f(y);

            fun checkElement(
                element: MaximalPath.Element,
                missingCoverage: MutableMap<ResolvedName, MutableSet<MaximalPathIndex>>,
            ) {
                val reads = readsAndWrites.readsByPathElement[element]
                    ?: emptyList()
                for (read in reads) {
                    val name = read.name
                    when (name) {
                        // Check liveness below
                        is InternalModularName -> {}
                        is ExportedName -> {
                            val exportingModule = (name.origin as? ModuleNamingContext)?.owner
                            if (exportingModule != module) {
                                // Imported names are assumed to be initialized from
                                // the time the exporter finished Stage.Export.
                                continue
                            }
                        }
                        // BuiltinNames are assumed to be initialized before any
                        // module starts loading.
                        is BuiltinName -> continue
                    }

                    if (name in locallyDeclaredTypes) {
                        // TODO: if this is a type reference, check if its used in `new`
                        // then require that local names used there are live here.
                        continue
                    }

                    if (name !in readsAndWrites.localNames) {
                        // Propagate that to the outer function.
                        val pos = read.pos
                        if (pos != null) {
                            freeNames.putMultiSet(name, pos)
                        }
                        continue
                    }

                    val edge = read.tree?.incoming ?: continue
                    val missing = missingCoverage[name]
                    if (missing == null || missing.isNotEmpty()) {
                        debug {
                            group("Uncovered @ ${edge.target.pos}") {
                                group("Code") {
                                    edge.target.toPseudoCode(textOutput)
                                }
                                root.dereference(read.containingPathElement!!.ref)?.let { stmtEdge ->
                                    group("Containing statement") {
                                        stmtEdge.target.toPseudoCode(textOutput)
                                    }
                                }
                            }
                        }

                        val isUnnecessarySyntheticRead = run unnecessary@{
                            // If it's a temporary at statement position, it's an inserted read
                            // necessary for weaving but not semantically significant.
                            // We couldn't remove this in Weaver because that needs to happen
                            // before MakeResultsExplicit, but now we can.
                            if (read.name !is Temporary) {
                                return@unnecessary false
                            }
                            val container = read.containingPathElement
                                ?: return@unnecessary false
                            if (container.isCondition) {
                                // It's necessary if it's used in a branch condition.
                                return@unnecessary false
                            }
                            val statementEdge = root.dereference(container.ref)
                            statementEdge?.target == read.tree
                        }

                        if (isUnnecessarySyntheticRead) {
                            usesBeforeInit.add(edge to null)
                        } else {
                            val uncoveredPositions = missing?.map {
                                readsAndWrites.pathDiagnosticPositions.getValue(it)
                            } ?: listOf(readsAndWrites.pathDiagnosticPositions.getValue(element.pathIndex).leftEdge)
                            val problem = LogEntry(
                                level = Log.Error,
                                template = MessageTemplate.UseBeforeInitialization,
                                pos = edge.target.pos.pos,
                                values = listOf(name, uncoveredPositions),
                            )
                            usesBeforeInit.add(edge to problem)
                            problem.logTo(module.logSink)
                        }
                    }

                    val frees = freeNames[name]
                    if (frees != null) {
                        // TODO: check that it's free names are live here.
                        ignore(frees)
                    }
                }

                // TODO: check that each unnamed nested function's free names are live
                // at its containing element.

                // Update missing coverage.
                val decl = readsAndWrites.declsByPathElement[element]
                val declared = decl?.parts?.name?.content as ResolvedName?
                if (declared != null) {
                    missingCoverage.remove(declared)
                }
                readsAndWrites.writesByPathElement[element]?.forEach { write ->
                    missingCoverage.getOrPut(write.name) {
                        mutableSetOf()
                    }.clear()
                }
            }

            val forwardPathOrder = orderedPathIndices(paths, ForwardOrBack.Back)
            val missingCoverageAtEnd =
                mutableMapOf<MaximalPathIndex, MutableMap<ResolvedName, MutableSet<MaximalPathIndex>>>()
            missingCoverageAtEnd[MaximalPathIndex.beforeEntry] = run {
                val coverageForInputs = mutableMapOf<ResolvedName, MutableSet<MaximalPathIndex>>()
                inputParameters.forEach {
                    val name = it.parts?.name?.content as ResolvedName?
                    if (name != null) {
                        coverageForInputs[name] = mutableSetOf()
                    }
                }
                coverageForInputs
            }
            for (pathIndex in forwardPathOrder) {
                val path = paths[pathIndex]
                val missingCoverage = mutableMapOf<ResolvedName, MutableSet<MaximalPathIndex>>()
                val allForwardPreceders = buildList {
                    if (pathIndex == paths.entryPathIndex) {
                        add(MaximalPathIndex.beforeEntry)
                    }
                    path.preceders.mapNotNullTo(this) {
                        when (it.dir) {
                            ForwardOrBack.Forward -> it.pathIndex
                            else -> null
                        }
                    }
                }
                for ((positionInPrecederList, preceder) in allForwardPreceders.withIndex()) {
                    val precederCoverage = missingCoverageAtEnd.getValue(preceder)
                    forTwoMapsMutatingLeft(missingCoverage, precederCoverage) { zippedEntry ->
                        when (zippedEntry) {
                            is ZippedEntry.RightOnly -> {
                                // It is written in this preceder, but not in preceders that we processed
                                // earlier in the loop.
                                val missing = mutableSetOf<MaximalPathIndex>()
                                missingCoverage[zippedEntry.key] = missing
                                missing.addAll(allForwardPreceders.subList(0, positionInPrecederList))
                                missing.addAll(zippedEntry.right)
                            }
                            is ZippedEntry.LeftOnly -> {
                                // Some earlier preceder wrote it, but not this one.
                                zippedEntry.left.add(preceder)
                            }
                            is ZippedEntry.Both -> {
                                zippedEntry.left.addAll(zippedEntry.right)
                            }
                        }
                    }
                }

                // Now that we know what was defined at the start of the paths, and
                // where we're missing coverage, walk the elements.
                for (element in path.elements) {
                    checkElement(element, missingCoverage)
                }
                for (follower in path.followers) {
                    follower.condition?.let { checkElement(it, missingCoverage) }
                }

                missingCoverageAtEnd[pathIndex] = missingCoverage
            }

            return FreeNames(
                freeNames.mapValues { (_, positions) ->
                    positions.toSet()
                },
            )
        }

        val moduleReturnDecl = moduleOutputName?.let {
            moduleRoot.children.mapFirst { rootChild ->
                if (rootChild is DeclTree && rootChild.parts?.name?.content == it) {
                    rootChild
                } else {
                    null
                }
            }
        }

        processBody(
            inputParameters = emptyList(),
            returnDecl = moduleReturnDecl,
            root = moduleRoot,
        )
    }

    private fun fixupTreeAndReportProblems(problems: List<Pair<TEdge, LogEntry?>>) {
        for ((edgeToReplace, problem) in problems) {
            edgeToReplace.replace {
                if (problem != null) {
                    val type = edgeToReplace.target.typeInferences?.type
                    val errorTypeInferences = type?.let { type ->
                        val sig = if (type.isVoidLike) {
                            ErrorFn.voidSig
                        } else {
                            ErrorFn.genericSig
                        }
                        CallTypeInferences(
                            type,
                            typeFromSignature(sig),
                            buildMap {
                                val tf = sig.typeFormals.firstOrNull()
                                if (tf != null) {
                                    this[tf] = type
                                }
                            },
                            listOf(),
                        )
                    }
                    Call(type = errorTypeInferences) {
                        V(errorFn, type = errorTypeInferences?.variant)
                        V(Value(problem, TProblem), type = Types.problem.type)
                    }
                } else {
                    V(void, type = Types.void.type)
                }
            }
        }
    }

    /**
     * Relates names not defined in a body to non-empty sets of positions of uses.
     * This includes names of types used via reified type value references.
     */
    private data class FreeNames(
        val names: Map<ResolvedName, Set<Position>>,
    )

    private val debugging get() = DEBUG && !module.isImplicits

    @Suppress("unused")
    inline fun debug(diagnosticBlock: Console.() -> Unit) {
        if (debugging) {
            debugConsole?.diagnosticBlock()
        }
    }

    @Suppress("unused")
    private val debugConsole get() = if (debugging) console else null
}
