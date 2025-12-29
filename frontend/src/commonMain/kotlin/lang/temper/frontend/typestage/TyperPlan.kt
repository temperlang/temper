package lang.temper.frontend.typestage

import lang.temper.builtin.isHandlerScopeCall
import lang.temper.builtin.isSetPropertyCall
import lang.temper.common.ForwardOrBack
import lang.temper.common.addTransitiveClosure
import lang.temper.common.buildSetMultimap
import lang.temper.common.mergeMaps
import lang.temper.common.putMultiList
import lang.temper.common.putMultiSet
import lang.temper.frontend.syntax.isAssignment
import lang.temper.name.ModularName
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.MemberShape
import lang.temper.type.StaticType
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.TNull
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.blockPartialEvaluationOrder
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.fromTypeSymbol
import lang.temper.value.functionContained
import lang.temper.value.isNullaryNeverCall
import lang.temper.value.orderedPathIndices
import lang.temper.value.staticTypeContained
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.typeSymbol
import lang.temper.value.vConstructorSymbol
import lang.temper.value.valueContained
import lang.temper.value.void
import lang.temper.value.wordSymbol

/**
 * A [TyperPlan] explains how to infer types for a module.
 *
 * It collects information about the tree to answer questions like:
 *
 * - Which variables have the same type as which others.
 * - Which assignments count as "initialization."
 * - What order do we infer types in?
 * - Which calls must be inter-twined?
 * - Which local names correspond to members of locally declared types?
 *
 * ## Assumptions about tree structure
 * The typer runs after weaving, so we assume the following are true.
 *
 * - All control flow structures have been converted to [blocks][BlockTree].
 * - All blocks are either function or module bodies.
 * - All calls to `=` are direct children of a block &dagger;
 * - All calls to `hs` are one of:
 *   - the direct child of a block &dagger;
 *   - the right-hand-side of an assignment
 *
 * The assumptions about `hs` and `=` allow us to more easily correlate the order we infer types
 * in with relationships between variables and declared types.
 *
 * &dagger;: or are a function or module body
 *
 * ## Which variables must have the same type as which others?
 * If a temporary was introduced to collect the result of a variable across multiple branches
 * then we want to recognize that.
 *
 * ```temper
 * let x: List<String> = if (b) { f() } else { g() };
 * ```
 *
 * The above may reach the Typer as
 *
 * ```temper
 * let x: List<String>;
 * var t#0;
 * if (b) { t#0 = f(); } else { t#0 = g(); }
 * x = t#0;
 * ```
 *
 * We need to use declared types on temporaries that were introduced to collect the value
 * for a variable.
 *
 * If `f` were a generic function of type `fn <T>(): List<T>`, we need to use the context type,
 * `List<String>`, to recognize that `<T>` binds to `<String>` at that call site.
 * (`f` could construct and return an empty list)
 *
 * When a local variable without a declared type is assigned to exactly one other variable,
 * we assume that the assigned has the same type as the assignee; in this case, that `t#0` has
 * the same type as `x`.
 *
 * [DeclTree]s are used to represent both local variables and function inputs, but we do not infer
 * types for function inputs.
 *
 * ## Which assignments count as *initialization*?
 *
 * ```temper
 * let a = 0;
 * let b;
 * b = 1;
 * ```
 *
 * It's reasonable to conclude that both `a` and `b` have type `Int`.
 *
 * But we don't want to entangle to many assignments.
 *
 * ```temper
 * var i = 0;
 * // Lots of other statements
 * i += f(i);
 * ```
 *
 * Having reliable type information about `i` when we type `f` is useful if `f` is a generic
 * function, which is complicated since `+` has both `Int` and `Double` variants.
 *
 * It's also important to limit how many statements we involve in typing `i`.  Otherwise, type
 * errors spread to many tree nodes, causing the IDE to mark many statements with red wiggles.
 *
 * We define "initializers" for a variable *v* as:
 * an assignment to *v* that is not preceded, along any branch (ignoring `continue`) branches by
 * an assignment to *v*.
 *
 * If a read of *v* appears along any branch before an initializer, then it will be recognized
 * as invalid by our use-before-init checks (TODO: implement these).
 *
 * In the first code block above, after `let b;`, the assignment `b = 1` is an initializer even
 * though it is not syntactically part of the declaration.
 * We want to recognize non-syntactic initializers since Temper needs:
 * - to be a good target language for code generators, and
 * - macros should similarly be able to generate subtrees that type well
 *   without having to encode syntactic cues.
 *
 * Let's look at some examples.
 *
 * ```temper
 * let a = 0;
 * // (0) is the initializer for `a`
 *
 * let b;
 * b = 1;
 * // (1) is the initializer for `b`
 *
 * var c;
 * c = c + 1;
 * // (c + 1) is the initializer for `c` but the read of `c` in the initializer types to Invalid.
 *
 * let d;
 * if (condition) {
 *     d = f();
 * } else {
 *     d = null;
 * }
 * // (f()) and (null) are both initializers for `d`.
 *
 * var e = null;
 * e = foo();
 * // (null) is the initializer for `e`.  (foo()) is not.
 * // The inferred type here is *Never<Invalid>?* which may be surprising.
 *
 *
 * let f;
 * // There are zero initializers for `f`.
 * // The union of zero types is *Never* so we infer *Never* as the type for `f`.
 * // Any reads of it though should type as *Invalid* since they must be use-before-initialization
 * // errors.
 *
 * let g: T;
 * g = whatever();
 * // g has a declared type, so its initializers are never used to infer a type.
 * // Instead, its declared type is used as a context type for all expressions assigned to it,
 * // whether initializers or not.
 *
 * let h;
 * if (condition) {
 *     h = [];
 * } else {
 *     h = ["foo"];
 * }
 * // ([]) and (["foo"]) are both initializers for `h`.
 * // It would be nice to be able to use the type information from `(["foo"])` to infer that
 * // type of ([]) is an empty list of strings.
 * // That inference does not fall out of this.
 *
 * // TODO: perhaps filter out initializers that need type context and exclude them
 * // so that we do not infer `List<Never> | List<String>` for `h`.
 * // Do not inter-twine context-free initializers with ones that do not.
 *
 * let i = mayFail(x) orelse panic();
 * // The `orelse` traps the failure and turns it into panic.  This is a user-assertion
 * // that `mayFail` won't fail for `x`.
 * // That desugars to a use of a temporary.  `mayFail(x)` is an initializer but
 * // `panic()` should not be.  Panic has a signature like `<T>() -> T` so we need to
 * // infer a type parameter from it, but cannot based on inputs.
 * // By treating `mayFail(x)` and moving calls to context-requiring operators like
 * // `panic()`, that have no explicit type parameters, to the end of the typer order,
 * // we can infer `i`'s type based on `mayFail(x)` and propagate that as an upper
 * // bound on the `panic` call's return type.
 * ```
 *
 * ## What order do we infer types in?
 *
 * The goal of order inference operations is to make sure we have the most type information
 * available, while keeping constraint sets small.
 * Large constraint sets take time to solve and a contradiction in one causes the IDE to render
 * all related code as errors.
 * Smaller constraint sets are faster to solve, and errors are usually easier for the programmer
 * to localize.
 *
 * Our inference order is macro-call order: we start at the start of a module, and when entering
 * a block, visit children that flow into a node (ignoring `continue` edges) before the node
 * flowed into.  Some examples:
 *
 * ```temper
 * let x;           // ①
 * if (condition) { // ②
 *   x = 1;         // ③
 * } else {
 *   x = 2;         // ④
 * }                // ⑤ after the branches rejoin
 * f(x);            // ⑥
 * ```
 *
 * This means advancing down all branches that join at a point before
 * proceeding past the join point.
 * ⑤ marks the join of the separate branches through ③ and ④ above.
 *
 * In that, the order of typing operations is:
 *
 * 1. Type the declaration as `void`.  Declarations do not produce a result.
 *    This is separate from typing the declared variable.  That happens later
 * 2. Type `condition` with context type `Boolean` since it appears in a branch condition.
 *    That branch flows into the two assignments to `x`.
 * 3. Type the first assignment to `x`. Which is first is arbitrary but deterministic.
 * 4. Type the second assignment to `x`.
 * 5. Now that the number of *outstanding initializing assignments* for `x` is zero, we can
 *    record the inferred type of `x` as the union of those types, and push that back to
 *    the `let x` declaration node's [name][LeftNameLeaf].
 * 6. Type `f()` which involves multiple steps:
 *    a. Type `f`.  We need to type that which is called before typing the call.
 *    b. Type any arguments to `f` which are functions themselves. Such functions might need
 *       the type of `f` to know their own signature types if unspecified in source.
 *    c. Type `f()`.  Use the type of `f` and the types of its arguments to compute the actual
 *       return type which may differ from `f`'s return type if it is a generic function.
 * 7. Type nullary never-ish calls like `panic()` and `bubble()`.
 *
 * (TODO: in 5.a, we might need to inter-twine calls that compute callees as in `g()(x)`
 * to compute type actuals for `g()` based on information from `x`?
 * For example for a function type parameter that appears in the type of a function returned:
 * ```temper
 * let g<T>(): (fn (): (fn (T): T)) { return fn (x: T): T { return x } }
 * ```
 * For now, we are ignoring this case and leaving it as a TBD wrinkle in call inter-twining since
 * we do need to type `g` before `g()`.  What we are inter-twining is two calls, one of which
 * happens to be a callee.)
 *
 * This order does not handle all cases.
 * For example, with mutually referencing functions and type definitions, it's impossible to order
 * things so that referrers always follow their referents.
 *
 * ```temper
 * let isEven(x: Int): Boolean { x == 0 || isOdd(x - sign(x)) }
 * let isOdd(x: Int): Boolean { !isEven(x) }
 * ```
 *
 * To type the body of `isEven`, we need to know the type of `isOdd`.  Since these are co-recursive,
 * there is no possible ordering that disentangles this.
 *
 * The Typer compensates for this problem by pre-typing names whose initializers are all values
 * (including pure function values) or [FunTree]s that have a complete signature.
 *
 * This approach also addresses mutually-referencing type definitions, since the define stage turns
 * member definitions into top-level `let`s.
 *
 * ## Which calls must be inter-twined?
 * Sometimes, it's not possible to type arguments to a call before a call.
 *
 * ```temper
 * let ls: List<String> = f(g([]))
 * ```
 *
 * - If `g` takes a `List<Int>` then `[]` is an empty `List<Int>`.
 * - But if `g` is generic and takes and returns a `List<T>` then the type of `[]` is based on
 *   `f`'s argument type.
 *   - If `f` takes a `List<Int>` then `g`'s type parameter `<T>` binds to `Int` and the `[]` is
 *     an empty `List<Int>`.
 *   - But if `f` is generic then the context type, `List<String>` must be used to determine the
 *     type of both `f` and `g`'s type parameters and the type of the empty list.
 *
 * *Inter-twined* calls are calls that need to be solved at the same time, because the binding
 * of one's type parameters depends on the binding of another call's, and the usual order of typing
 * (where we type arguments before typing the call) will not resolve that.
 *
 * (When we talk about calls, we're including implicit function calls like the `[]` list
 *  constructor)
 *
 * Inter-twining checks include several discrete steps:
 * 1. Find the callee type (for caveats on how discrete this can be, see the TODO above)
 * 2. Look through variables to find implicitly nested function calls.
 *    If an argument is a variable read like `t#0` and `t#0` is assigned exactly once to a
 *    call (or a transitive variable read), and the variables are only read once, then the
 *    call is considered nested.
 * 3. If a nested call is generic, and it has a type parameter, *T*, that does not appear in
 *    the type for a provided argument that is not an inter-twined, nested call; and either *T*
 *    does not appear in the return type or there is no context type to compare the return type to,
 *    then:
 *    - If the nesting call's corresponding parameter does not depend on ant of the nesting
 *      callee's type parameters, then that parameter type can be used as a context type for the
 *      nested call.
 *    - Else, the two calls need to be inter-twined.
 *
 * Note: inter-twining cannot be determined a priori, since not all callee types can be resolved
 * early.
 * As we walk the typing order, we need to keep inter-twined calls suspended until we can type
 * their use.
 *
 * We do a priori, determine the relationship between variables that are assigned once,
 * transitively, to a call,
 *
 * The "and the variables are only read once" requirement above is necessary so that we can
 * suspend typing of nesting, generic calls.
 *
 * ```temper
 * t#1 = nested();
 * t#2 = t#1;
 * nesting(t#1);
 * ```
 *
 * The type of `nested()` is needed to type t#2 before we reach the nesting call.
 * This arrangement would not happen if the calls were syntactically nested, so, to keep our
 * typing ordering simple, we do not inter-twine these calls and instead fall back to other
 * strategies: type parameters bind to their upper bound.
 *
 * ## Why is typing for functions missing signature types deferred?
 *
 * The following example demonstrates deferred block signature typing:
 *
 * ```temper
 * let text = [3, 4, 5].join(", ") { n => n.toString() };
 * ```
 * In this example, `join` takes a function of type `fn (Int): String`, so `n` must be of type
 * `Int`, and the return type must be `String`, so both can be left unspecified. However, we
 * first need to resolve the type of callee `join`. Further, we can't type the contents of the
 * lambda block until we know the type of `n`, or else we wouldn't know that `toString` is an
 * available method. So we first type the callee, then the lambda signature, then the call to
 * `join`, and *then* the contents of the lambda block.
 *
 * In the future, we might type the contents of the lambda as soon as its parameter types are
 * known, but that isn't done today. This change could enable return type inference from internal
 * lambda return values, which isn't currently supported.
 *
 * Also, when inferring missing signature types, any generic types on the callee must be already
 * resolved without the help of the lambda block. If the callee needs help from the lambda block,
 * specify types explicitly on it. This limitation might be improved in the future via intertwined
 * typing.
 *
 * ## Which local names correspond to members of locally declared types?
 * Each `class` or `interface` declaration converts to local variable declarations for each
 * member and the type name.
 *
 * To type uses of local types, including `new LocallyDeclaredType()` and
 * `myInstanceOfLocallyDeclaredType.member`, we need to know which local names correspond to members
 * so that when we figure out the type of the local name, we can associate it with the member via
 * [lang.temper.type.VisibleMemberShape.staticType].
 */
internal class TyperPlan(val root: BlockTree, returnName: ResolvedName?) {
    /**
     * A map such that `.get(toVar).contains(fromVar)` means we can infer the type of *toVar* from
     * *fromVar*.
     *
     * This is computed from the assignments relationships set when there is an assignment
     * of the form:
     * - `from = to` where `to` has no declared type, and that is the only use of `to`, or
     * - `setp(from, this..., to)`
     * - there is a transitive relationship like (from, other) and (other, to) where both
     *   match one of the relationships above.
     */
    val mayInferTypeForVariableFrom: Map<TemperName, Set<TemperName>>
    val nameToReceiver: Map<TemperName, Set<TemperName>>

    /** Subtrees in an order convenient to compute their types. */
    val typeOrder: List<Tree>

    /** For each assigned name, the right-hand-sides assigned to it. */
    val initializers: Map<TemperName, List<Tree>>
    val aliasedCalls: Map<TemperName, AliasedCall>

    /** Names of variables that have only one assignment site. */
    val singlyAssigned: Set<TemperName>
    val declarations: Map<TemperName, DeclTree>

    /** Edges that are used in branch conditions; their context type is `Boolean`. */
    val usedAsCondition: Set<TEdge>

    /** For answering which assignments correspond to members of locally declared types? */
    val namesToLocalMemberShapes: Map<TemperName, MemberShape>

    /**
     * Names of `return__123` variables declared for the results of functions and modules.
     * These names may bind to types that intersect [*BubbleType*][lang.temper.type.BubbleType]
     * unlike function inputs and local variables.
     */
    val returnNames: Set<TemperName>

    init {
        // // Tables we build to initialize members
        // For each name that appears as a DeclTree's LeftNameLeaf, the declarations.
        // It's a static error if that happens after the resolved name stage, but we collect them
        // all anyway.
        val declaredToDeclarations = mutableMapOf<TemperName, MutableList<DeclTree>>()
        // reads[k] is all RightNameLeaves, l, under root such that l.content == k
        val reads = mutableMapOf<TemperName, MutableList<NameLeaf>>()
        // For simple assignments of one variable to another like `left = right`,
        // or nested assignments like `left = right = ...`,
        // relates `right`'s to the list of `left`s paired with the call to the assignment operator.
        val nameToNameAssigned =
            mutableMapOf<TemperName, MutableList<Pair<TemperName, CallTree>>>()
        // A set of names that appear as function formal parameter names.
        // Parameters do not need to be initialized the way non-parameter DeclTrees do.
        val parameterNames = mutableSetOf<TemperName>()
        // All calls collected for later analysis
        val calls = mutableListOf<CallTree>()
        val assignmentCounts = mutableMapOf<TemperName, Int>()

        // A bucket for calls that depend on return context because their callee is a one
        // of a small number of intrinsics that have a type variable used only in their return type,
        // and because typing them early does not give pass-type context to containing trees.
        val endBucket = mutableListOf<Tree>()

        // Walk the root and extract info into data tables.  We do this in the order we want to
        // type, so we record every node under root in that order at the same time.
        fun walk(tree: Tree, typeOrder: MutableList<Tree>) {
            // Pull information out into tables
            when (tree) {
                is DeclTree -> {
                    val parts = tree.parts
                    if (parts != null) {
                        val name = parts.name.content
                        declaredToDeclarations.putMultiList(name, tree)
                        if (tree.isParameter) {
                            parameterNames.add(name)
                        }
                    }
                }
                is CallTree -> {
                    calls.add(tree)
                    var leftAndRight: Pair<Tree, Tree>? = when {
                        isAssignment(tree) -> {
                            val (_, left, right) = tree.children
                            left to right
                        }
                        isSetPropertyCall(tree) -> {
                            val (_, left, _, right) = tree.children
                            left to right
                        }
                        else -> null
                    }
                    var assignedName: TemperName? = null
                    if (leftAndRight != null) {
                        val (left, right) = leftAndRight
                        // Track variable aliasing
                        if (left is LeftNameLeaf) {
                            val leftName = left.content
                            assignedName = leftName

                            var rightName: TemperName? = null
                            if (right is RightNameLeaf) {
                                rightName = right.content
                            } else if (isAssignment(right)) {
                                // A chained assignment like
                                //     x0 = x1 = ...;
                                // is equivalent to
                                //     x1 = ...; x0 = x1;
                                val chainedLeft = right.child(1)
                                if (chainedLeft is NameLeaf) {
                                    rightName = chainedLeft.content
                                    reads.putMultiList(rightName, chainedLeft)
                                }
                            }
                            if (rightName != null) {
                                nameToNameAssigned.putMultiList(rightName, leftName to tree)
                            }
                        }
                    }
                    if (assignedName == null) {
                        val callee = tree.childOrNull(0)
                        val calleeFn = callee?.functionContained
                        if (calleeFn?.assignsArgumentOne == true) {
                            assignedName = (tree.childOrNull(1) as? LeftNameLeaf)?.content
                        }
                    }
                    // Keep assignment site counts up-to-date
                    if (assignedName != null) {
                        assignmentCounts[assignedName] = 1 +
                            (assignmentCounts[assignedName] ?: 0)
                    }
                }
                is RightNameLeaf -> reads.putMultiList(tree.content, tree)
                else -> Unit
            }
            // Recurse to children.
            val childOrder = if (tree is BlockTree) {
                blockPartialEvaluationOrder(tree)
            } else {
                0 until tree.size
            }
            var deferredTypeOrder: MutableList<Tree>? = null
            for (childIndex in childOrder) {
                val child = tree.child(childIndex)
                val buffer = if (shouldDeferFunTreeTyping(child)) {
                    // Children of deferred functions need to be after the fun parent is done.
                    if (deferredTypeOrder == null) {
                        deferredTypeOrder = mutableListOf()
                    }
                    deferredTypeOrder
                } else {
                    typeOrder
                }
                walk(child, buffer)
            }
            val inEndBucket = isNullaryNeverCall(tree)
            if (inEndBucket) {
                endBucket.add(tree)
            } else if (!shouldDeferFunTreeTyping(tree)) {
                // Add if it's not deferred entirely.
                typeOrder.add(tree)
            }
            if (deferredTypeOrder != null) {
                // Add deferred descendents after the current node rather than before.
                typeOrder.addAll(deferredTypeOrder)
            }
            if (tree is BlockTree) {
                // Just throw types on floating `void`s.
                // `void` is used as a placeholder for things that are not really useful,
                // and may be skipped in various representations.
                // Just throw type *Void* on them.
                val floatingVoidType = BasicTypeInferences(WellKnownTypes.voidType, emptyList())
                for (childIndex in tree.children.indices.toSet() - childOrder.toSet()) {
                    val child = tree.child(childIndex)
                    if (child is ValueLeaf && child.content == void && child.typeInferences == null) {
                        child.typeInferences = floatingVoidType
                    }
                }
            }
        }
        val typeOrder = mutableListOf<Tree>()
        walk(root, typeOrder)
        typeOrder.addAll(endBucket)

        val nameToReceiver = buildSetMultimap {
            for ((source, receiverAndCallList) in nameToNameAssigned) {
                for ((receiver) in receiverAndCallList) {
                    putMultiSet(source, receiver)
                }
            }
        }

        // Relate MemberShape.name to Members of locally declared types
        val nameToLocalMemberShapes = mutableMapOf<TemperName, MemberShape>()
        run {
            val localShapes = mutableSetOf<TypeShape>()
            declaredToDeclarations.values.forEach { declTrees ->
                declTrees.forEach { declTree ->
                    val metadata = declTree.parts?.metadataSymbolMap ?: emptyMap()
                    // Look at `@fromType(TypeReference) let` and `@typeDecl(TypeReference)`
                    val typeTree = metadata[fromTypeSymbol] ?: metadata[typeDeclSymbol]
                    val locallyDeclaredType = typeTree?.target?.typeShapeAtLeafOrNull
                    if (locallyDeclaredType != null && locallyDeclaredType !in localShapes) {
                        localShapes.add(locallyDeclaredType)
                        locallyDeclaredType.members.forEach {
                            nameToLocalMemberShapes[it.name] = it
                        }
                    }
                }
            }
        }

        val mayInferTypeForVariableFrom = mutableMapOf<TemperName, MutableSet<TemperName>>()
        // Look for ways to infer the types of un-typed variables from assignments.
        for ((declared, declarations) in declaredToDeclarations) {
            // We only infer from how they're used for temporaries.
            // Otherwise, we just infer from assignments to declarations, not from them.
            // The key issue is that we want to be able to chain through to literal inference.
            if (declared !is Temporary) { continue }
            // We don't infer types for parameters.
            if (declared in parameterNames || declarations.size != 1) { continue }
            val parts = declarations[0].parts ?: continue
            // Don't infer types for names that already have declared types.
            if (parts.type != null) { continue }

            // If it's read once, in an assignment, then use the type from the assignee.
            if (reads[declared]?.size == 1) {
                val assignedTo = nameToNameAssigned[declared]
                if (assignedTo?.size == 1) {
                    mayInferTypeForVariableFrom.putMultiSet(declared, assignedTo[0].first)
                }
            }
        }
        addTransitiveClosure(mayInferTypeForVariableFrom)

        // Condition edges have context type Boolean
        val usedAsCondition = mutableSetOf<TEdge>()
        // Finding initializers is flow-sensitive; we need to keep track of mentions of the assigned
        // name prior, and for each of parallel branches.
        // So we do a second pass over the module tree instead of trying to reuse the walk above.
        val initializers = buildSetMultimap {
            // We need to collect use information from all paths that reach a join point in a CFG
            fun findInitializers(
                tree: Tree,
                usesBefore: UsesBeforeMap,
            ): UsesBeforeMap {
                var usesAfter = usesBefore // Updated as we process tree
                if (isAssignment(tree)) {
                    check(tree is CallTree) // Call to `=`
                    val assigned = tree.child(1)
                    val assignedName = (assigned as? LeftNameLeaf)?.content
                    if (assignedName is ModularName) {
                        val assignee = tree.child(2)
                        val useBefore = usesBefore[assignedName] ?: UsesBefore.NotInitialized
                        val isAssignedNull = TNull.unpackOrNull(assignee.valueContained) == TNull.Null
                        when {
                            isNullaryNeverCall(assignee) -> {}
                            useBefore == UsesBefore.InitializedNotAlwaysNull -> {}
                            isAssignedNull && useBefore == UsesBefore.NotInitialized -> {
                                putMultiSet(assignedName, assignee)
                                usesAfter = usesAfter.withEntry(assignedName, UsesBefore.InitializedNull)
                            }
                            !isAssignedNull -> {
                                putMultiSet(assignedName, assignee)
                                usesAfter = usesAfter.withEntry(assignedName, UsesBefore.InitializedNotAlwaysNull)
                            }
                        }
                    }
                }
                if (tree is BlockTree) {
                    // The minimal set of maximal paths breaks the flow graph into sequences of
                    // subtrees that are executed in order.
                    val paths = forwardMaximalPaths(tree, assumeFailureCanHappen = true)

                    // Walk the basic block graph again keeping track of which names have
                    // been assigned what possible values.
                    val usesBeforePath = mutableMapOf(paths.entryPathIndex to usesAfter)

                    val pathIndices = orderedPathIndices(paths, ForwardOrBack.Back)
                    for (pathIndex in pathIndices) {
                        val path = paths[pathIndex]
                        for (pathElement in path.elementsAndConditions) {
                            if (pathElement.isCondition) {
                                val ref = pathElement.ref
                                tree.dereference(ref)?.let {
                                    usedAsCondition.add(it)
                                }
                            }
                        }

                        var usesForPath = usesBeforePath.getValue(pathIndex)
                        for (element in path.elements) {
                            val edge = tree.dereference(element.ref) ?: continue
                            val child = edge.target
                            usesForPath = findInitializers(child, usesForPath)
                        }

                        for (follower in path.followers) {
                            val condEdge = follower.condition?.ref?.let { tree.dereference(it) }
                            if (condEdge != null) {
                                usesForPath = findInitializers(condEdge.target, usesForPath)
                            }
                            if (follower.dir == ForwardOrBack.Forward) {
                                val followerIndex = follower.pathIndex
                                if (followerIndex != null) {
                                    usesBeforePath[followerIndex] =
                                        mergeUsesBeforeAsBranchesJoin(usesForPath, usesBeforePath[followerIndex])
                                }
                            }
                        }
                    }

                    val usesAtExits: UsesBeforeMap? = run {
                        var merged: UsesBeforeMap? = null
                        for (terminalPathExit in paths.exitPathIndices + paths.failExitPathIndices) {
                            merged = mergeUsesBeforeAsBranchesJoin(usesBeforePath.getValue(terminalPathExit), merged)
                        }
                        merged
                    }
                    if (usesAtExits != null) { // The block exits
                        usesAfter = usesAtExits
                    }
                } else {
                    for (child in tree.children) {
                        usesAfter = findInitializers(child, usesAfter)
                    }
                }
                return usesAfter
            }
            findInitializers(root, emptyMap())
        }

        val aliasedCalls = mutableListOf<AliasedCall>()
        for (call in calls) {
            if (isAssignment(call) || isHandlerScopeCall(call)) { continue }
            var edge = call.incoming!! // safe because root is a block, not a CallTree
            val hs: CallTree? = run {
                val parent = edge.source
                if (parent is CallTree && isHandlerScopeCall(parent) && edge.edgeIndex == 2) {
                    edge = parent.incoming!! // root is a block
                    parent
                } else {
                    null
                }
            }
            val assignment: CallTree? = run {
                val parent = edge.source
                if (parent is CallTree && isAssignment(parent) && edge.edgeIndex == 2) {
                    parent
                } else {
                    null
                }
            }
            if (assignment != null) {
                var name = (assignment.child(1) as? LeftNameLeaf)?.content
                val followed = mutableSetOf<TemperName>()
                while (name != null && reads[name]?.size == 1) {
                    if (name in followed) {
                        name = null
                        break
                    }
                    // Do not infinitely loop if there is a cycle of assignments.
                    //    a = b; b = a
                    followed.add(name)
                    val transitiveAlias = nameToNameAssigned[name]
                    if (transitiveAlias?.size == 1) {
                        name = transitiveAlias[0].first
                    } else {
                        break
                    }
                }
                val readsForName = reads[name]
                if (readsForName?.size == 1) {
                    check(name != null) // Safe because name is a valid key into reads
                    aliasedCalls.add(
                        AliasedCall(
                            aliased = call,
                            hs = hs,
                            assignment = assignment,
                            alias = name,
                            use = readsForName[0],
                        ),
                    )
                }
            }
        }

        val returnNames = mutableSetOf<TemperName>()
        if (returnName != null) { returnNames.add(returnName) }
        for ((name, decls) in declaredToDeclarations) {
            if (decls.size == 1) {
                val decl = decls[0]
                val parent = decl.incoming?.source
                if (parent is FunTree && parent.parts?.returnDecl == decl) {
                    returnNames.add(name)
                }
            }
        }

        this.mayInferTypeForVariableFrom =
            mayInferTypeForVariableFrom.mapValues { it.value.toSet() }
        this.nameToReceiver = nameToReceiver
        this.typeOrder = typeOrder.toList()
        this.initializers = initializers.mapValues { it.value.toList() }
        this.aliasedCalls = aliasedCalls.associateBy { it.alias }
        this.singlyAssigned = assignmentCounts.filter { it.value == 1 }.keys.toSet()
        this.declarations = declaredToDeclarations.mapNotNull { (name, decls) ->
            if (decls.size == 1) {
                name to decls[0]
            } else {
                null
            }
        }.toMap()
        this.usedAsCondition = usedAsCondition.toSet()
        this.namesToLocalMemberShapes = nameToLocalMemberShapes.toMap()
        this.returnNames = returnNames.toSet()
    }

    /**
     * A call that is aliased and which may be used by that alias in another call.
     *
     * An aliased call is one that:
     * - appears as the right-side of an assignment, or in an `hs` call that is the right-hand side
     *   of an assignment
     * - is assigned to a name which is read in one location or which is assigned to a variable that
     *   is read in one location, or etc. transitively.
     *
     * Aliased calls are interesting when deciding whether generic calls should be inter-twined as
     * explained above.
     */
    data class AliasedCall(
        /** The call that's aliased */
        val aliased: CallTree,
        /** Any call to [lang.temper.builtin.BuiltinFuns.handlerScope] that wraps [aliased] */
        val hs: CallTree?,
        /** The call to the assignment where [hs]?:[aliased] is the right-hand-side. */
        val assignment: CallTree,
        /**
         * The name by which [aliased] is aliased.  If there are a sequence of calls, this is the
         * final name.
         */
        val alias: TemperName,
        /**
         * The sole read of the [alias].
         */
        val use: NameLeaf,
    )

    fun declaredType(name: TemperName): StaticType? =
        declarations[name]?.parts?.metadataSymbolMap[typeSymbol]?.staticTypeContained
}

private val DeclTree.isParameter: Boolean
    get() {
        val parent = incoming?.source
        return parent is FunTree && this in (parent.parts?.formals ?: emptySet())
    }

/** Hold off on fun trees until we have more callee information. */
internal fun shouldDeferFunTreeTyping(tree: Tree) =
    tree is FunTree && !tree.isConstructor() && tree.anyUntypedSigParts()

internal fun FunTree.anyUntypedSigParts(): Boolean {
    val parts = this.parts ?: return false // No sig to be typed?
    val allTyped = parts.formals.all { it.parts?.type != null } && parts.returnDecl?.parts?.type != null
    return !allTyped
}

internal fun FunTree.isConstructor() = parts?.metadataSymbolMap?.get(wordSymbol)?.target?.content == vConstructorSymbol

/** Used to decide whether something is an initializer. */
private enum class UsesBefore {
    NotInitialized,
    InitializedNull,
    InitializedNotAlwaysNull,
}

private typealias UsesBeforeMap = Map<ModularName, UsesBefore>

private fun <K, V> Map<K, V>.withEntry(k: K, v: V): Map<K, V> = buildMap {
    this@buildMap.putAll(this@withEntry)
    this@buildMap[k] = v
}

/**
 * Merges the joining across two branches.
 *
 * @param a `null` means no terminating branch, which is distinct from the empty map
 * @param b `null` means no terminating branch, which is distinct from the empty map
 */
private fun <USES_BEFORE_MAP : UsesBeforeMap?> mergeUsesBeforeAsBranchesJoin(
    a: USES_BEFORE_MAP,
    b: UsesBeforeMap?,
): USES_BEFORE_MAP = when {
    a != null && b != null -> {
        val merged = mergeMaps(
            zeroValue = UsesBefore.NotInitialized,
            a = a,
            b = b,
        ) { av, bv ->
            when {
                // If it's the same across all paths, early out.
                av == bv -> av
                // If it was initialized across all paths and to a
                // non-null value along any path, then we're done.
                av == UsesBefore.InitializedNotAlwaysNull ||
                    bv == UsesBefore.InitializedNotAlwaysNull ->
                    UsesBefore.InitializedNotAlwaysNull
                // It was initialized across some branch.  Use that.
                av == UsesBefore.InitializedNull ||
                    bv == UsesBefore.InitializedNull ->
                    UsesBefore.InitializedNull
                // Not reachable since we know the two values differ,
                // but have eliminated 2 of 3 possibilities above.
                // If someone adds another enum entry this will let them
                // know to change this.
                else -> error("$a $b")
            }
        }
        @Suppress("UNCHECKED_CAST") // Fine as long as USES_BEFORE_MAP is not explicitly bound to Nothing.
        merged as USES_BEFORE_MAP
    }
    b != null -> {
        @Suppress("UNCHECKED_CAST")
        b as USES_BEFORE_MAP
    }
    else -> a
}
