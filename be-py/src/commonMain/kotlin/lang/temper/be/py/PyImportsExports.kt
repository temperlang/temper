package lang.temper.be.py

import lang.temper.name.OutName

/**
 * A sink that accepts an exportable identifier. This interface checks whether names look exportable.
 * Names starting with `_` are private and not exportable. Temporary names are not exportable.
 * This can also accept `delete` messages; any identifiers that are deleted (ever) are assumed not to be exportable.
 */
fun interface ExportSink {
    /** Base method for this functional interface. */
    fun act(name: PyDottedIdentifier, delete: Boolean)

    operator fun invoke(base: PyDottedIdentifier?, name: Py.Name, delete: Boolean = false): PyDottedIdentifier? =
        check(base, name.outName, delete)
    operator fun invoke(base: PyDottedIdentifier?, name: Py.Identifier, delete: Boolean = false): PyDottedIdentifier? =
        check(base, name.outName, delete)
    operator fun invoke(base: PyDottedIdentifier?, name: OutName, delete: Boolean = false): PyDottedIdentifier? =
        check(base, name, delete)

    /** Check if the name is exportable and act on it. */
    fun check(base: PyDottedIdentifier?, name: OutName, delete: Boolean): PyDottedIdentifier? =
        if (name.isExportable()) {
            base.dot(name).let {
                act(it, delete)
                it
            }
        } else {
            null
        }
}

/**
 * A sink that accepts names for import.
 */
fun interface ImportSink {
    operator fun invoke(name: Py.Name, exclude: Set<OutName>) = check(name.outName, exclude)
    operator fun invoke(name: Py.Identifier, exclude: Set<OutName>) = check(name.outName, exclude)
    fun check(name: OutName, exclude: Set<OutName>) {
        if (name.isExportable() && name !in exclude) {
            act(name)
        }
    }
    fun act(name: OutName)
}

private fun OutName.isExportable(): Boolean = PyIdentifierGrammar.looksExportable(outputNameText)

private fun Py.Stmt.forEachChildStmt(act: (Py.Stmt) -> Unit) {
    when (this) {
        is Py.FunctionDef -> {
            body.forEach(act)
        }
        is Py.ClassDef -> {
            body.forEach(act)
        }
        is Py.For -> {
            body.forEach(act)
            orElse.forEach(act)
        }
        is Py.While -> {
            body.forEach(act)
            orElse.forEach(act)
        }
        is Py.If -> {
            body.forEach(act)
            elifs.forEach { elif -> elif.body.forEach(act) }
            orElse.forEach(act)
        }
        is Py.With -> {
            body.forEach(act)
        }
        is Py.Try -> {
            body.forEach(act)
            handlers.forEach { handler -> handler.body.forEach(act) }
            orElse.forEach(act)
            finalbody.forEach(act)
        }
        else -> {}
    }
}

private fun Py.Expr?.forEachChildExpr(act: (Py.Expr) -> Unit) {
    fun doArgs(args: Py.Arguments?) {
        args?.args?.forEach {
            it.annotation?.let(act)
            it.defaultValue?.let(act)
        }
    }
    fun doComprehensions(gens: List<Py.Comprehension>, vararg exprs: Py.Expr) {
        gens.forEach { comp ->
            act(comp.target)
            act(comp.iter)
            comp.ifs.forEach { compIf ->
                act(compIf.test)
            }
        }
        exprs.forEach(act)
    }
    fun actIf(value: Py.Expr?) {
        if (value != null) {
            act(value)
        }
    }
    when (this) {
        null -> {}
        is Py.Lambda -> {
            doArgs(this.args)
            act(body)
        }
        is Py.ListComp -> doComprehensions(this.generators, this.elt)
        is Py.SetComp -> doComprehensions(this.generators, this.elt)
        is Py.DictComp -> doComprehensions(this.generators, this.key, this.value)
        is Py.GeneratorComp -> doComprehensions(this.generators, this.elt)
        is Py.Name -> {}

        is Py.BinExpr -> {
            act(this.left)
            act(this.right)
        }
        is Py.UnaryExpr -> act(this.operand)
        is Py.IfExpr -> {
            act(this.test)
            act(this.body)
            act(this.orElse)
        }
        is Py.Dict -> {
            this.items.forEach { pair ->
                act(pair.key)
                act(pair.value)
            }
        }
        is Py.SetExpr -> {
            this.elts.forEach(act)
        }
        is Py.Await -> act(this.value)
        is Py.Yield -> actIf(this.value)
        is Py.YieldFrom -> actIf(this.value)
        is Py.Call -> {
            act(func)
            args.forEach { arg -> act(arg.value) }
        }
        is Py.Attribute -> act(this.value)
        is Py.Subscript -> {
            act(this.value)
            this.slice.forEach { slice ->
                when (slice) {
                    is Py.Expr -> act(slice)
                    is Py.Slice -> {
                        actIf(slice.lower)
                        actIf(slice.upper)
                        actIf(slice.step)
                    }
                }
            }
        }
        is Py.Starred -> act(this.value)
        is Py.ListExpr -> this.elts.forEach(act)
        is Py.Tuple -> this.elts.forEach(act)
        is Py.TypeStr -> act(this.x)
        is Py.Num -> {}
        is Py.Str -> {}
        is Py.Constant -> {}
    }
}

private fun Py.Tree.forEachLhsName(act: (Py.Name, Boolean) -> Unit) {
    when (this) {
        is Py.Assign -> this.targets.map { it to false }
        is Py.AugAssign -> listOf(this.target to false)
        is Py.AnnAssign -> listOf(this.target to false)
        is Py.Delete -> this.targets.map { it to true }
        else -> listOf()
    }.forEach { (expr, deletion) ->
        if (expr is Py.Name) {
            act(expr, deletion)
        }
    }
}

fun Py.Program.gatherExports(name: PyDottedIdentifier?, excludeImports: Boolean = false, sink: ExportSink) {
    body.gatherExports(name, excludeImports = excludeImports, sink = sink)
}

private fun List<Py.Stmt>.gatherExports(name: PyDottedIdentifier?, excludeImports: Boolean = false, sink: ExportSink) {
    forEach { stmt -> stmt.gatherStmtExports(name, excludeImports, sink) }
}

/**
 * Any assignment at the module level creates a name visible in the module.
 */
private fun Py.Stmt.gatherStmtExports(name: PyDottedIdentifier?, excludeImports: Boolean = false, sink: ExportSink) {
    when (this) {
        is Py.FunctionDef -> {
            sink(name, this.name)
        }
        is Py.ClassDef -> {
            // Only if the class itself is exportable, export any inner classes.
            sink(name, this.name)?.let { inName ->
                this.body.forEach { it.gatherClassExports(inName, sink) }
            }
        }
        is Py.ImportStmt -> if (!excludeImports) {
            simpleNames().forEach {
                sink.invoke(name, it)
            }
        }
        is Py.Assign, is Py.AnnAssign, is Py.Delete -> {
            this.forEachLhsName { inName, delete -> sink(name, inName, delete) }
        }
        is Py.For, is Py.While, is Py.If, is Py.With, is Py.Try -> {
            forEachChildStmt { stmt -> stmt.gatherStmtExports(name, excludeImports = excludeImports, sink = sink) }
        }
        else -> {}
    }
}

fun Py.ImportStmt.simpleNames() = sequence {
    when (val import = this@simpleNames) {
        is Py.Import -> import.names.forEach { alias ->
            // import `foo.bar.baz` creates the name `foo` which will always be a module
            // import `foo.bar.some_func as my_func` is likely useful
            alias.asname?.let { yield(it.outName) }
        }
        is Py.ImportFrom -> import.names.forEach { alias ->
            // from foo.bar.qux import
            when (val asName = alias.asname) {
                null -> alias.name.module.asSimpleName()?.let { yield(it) }
                else -> yield(asName.outName)
            }
        }
        is Py.ImportWildcardFrom -> {
            // Just punt for now, since we don't know what's being imported.
            // We also use this only in very limited cases.
        }
    }
}
private fun Py.Stmt.gatherClassExports(name: PyDottedIdentifier?, sink: ExportSink) {
    when (this) {
        is Py.ClassDef -> {
            // Only if the class itself is exportable, export any inner classes.
            sink(name, this.name)?.let { inName ->
                this.body.forEach { it.gatherClassExports(inName, sink) }
            }
        }
        is Py.For, is Py.While, is Py.If, is Py.With, is Py.Try -> {
            forEachChildStmt { stmt -> stmt.gatherClassExports(name, sink) }
        }
        is Py.Delete -> {
            this.forEachLhsName { inName, delete -> sink(name, inName, delete) }
        }
        else -> {}
    }
}

/**
 * The rules for imports are that they are not any of:
 *
 *   1. Private; imports must be exportable
 *   2. Exported or deleted.
 *   3. A formal argument.
 *   4. Declared in the same or parent scope.
 *
 *   Python's scoping rules are such that a name comes into existence in its scope when it's assigned.
 *   We're not going to get that granular, so if you declare a name at any point in the scope, it's not imported.
 *
 *   The implementation for Program illustrates the basic pattern.
 */
fun Py.Program.gatherImports(exclude: Set<OutName>, sink: ImportSink) {
    val exclusion = exclude.toMutableSet()
    body.forEach { stmt -> stmt.gatherNames(exclusion) }
    body.forEach { stmt ->
        stmt.gatherImports(sink, exclusion)
    }
}

private fun Py.Stmt.gatherNames(names: MutableSet<OutName>) {
    when (this) {
        is Py.FunctionDef -> {
            names.add(this.name.outName)
        }
        is Py.ClassDef -> {
            names.add(this.name.outName)
        }
        is Py.Assign -> this.targets.forEach { tgt -> tgt.gatherNames(names) }
        is Py.AugAssign -> this.target.gatherNames(names)
        is Py.AnnAssign -> this.target.gatherNames(names)
        is Py.For -> {
            target.gatherNames(names)
            forEachChildStmt { stmt -> stmt.gatherNames(names) }
        }
        is Py.With -> {
            this.items.forEach {
                it.optionalVars.gatherNames(names)
            }
            forEachChildStmt { stmt -> stmt.gatherNames(names) }
        }
        is Py.Try -> {
            this.handlers.forEach { handler ->
                handler.name?.let {
                    names.add(it.outName)
                }
            }
            forEachChildStmt { stmt -> stmt.gatherNames(names) }
        }
        is Py.While, is Py.If -> forEachChildStmt { stmt -> stmt.gatherNames(names) }
        else -> {}
    }
}

private fun Py.Stmt.gatherImports(sink: ImportSink, exclude: Set<OutName>) {
    when (this) {
        is Py.ClassDef -> {
            this.decoratorList.forEach { decorator -> decorator.gatherImports(sink, exclude) }
            // class "args" are the bases
            this.args.forEach { arg -> arg.gatherImports(sink, exclude) }
            val inNames = HashSet(exclude)
            this.body.forEach { stmt -> stmt.gatherNames(inNames) }
            this.body.forEach { stmt -> stmt.gatherImports(sink, inNames) }
        }
        is Py.FunctionDef -> {
            this.decoratorList.forEach { decorator -> decorator.gatherImports(sink, exclude) }
            this.args.gatherImports(sink, exclude)
            val inNames = HashSet(exclude)
            this.args.gatherNames(inNames)
            this.body.forEach { stmt -> stmt.gatherNames(inNames) }
            this.body.forEach { stmt -> stmt.gatherImports(sink, inNames) }
        }
        is Py.Return -> this.value.gatherImports(sink, exclude)
        is Py.Assign -> this.value.gatherImports(sink, exclude)
        is Py.AugAssign -> {
            this.value.gatherImports(sink, exclude)
        }
        is Py.AnnAssign -> {
            this.value.gatherImports(sink, exclude)
            this.annotation.gatherImports(sink, exclude)
        }
        is Py.For -> {
            this.iter.gatherImports(sink, exclude)
            this.forEachChildStmt { it.gatherImports(sink, exclude) }
        }
        is Py.While -> {
            this.test.gatherImports(sink, exclude)
            this.forEachChildStmt { it.gatherImports(sink, exclude) }
        }
        is Py.If -> {
            this.test.gatherImports(sink, exclude)
            this.elifs.forEach { it.test.gatherImports(sink, exclude) }
            this.forEachChildStmt { it.gatherImports(sink, exclude) }
        }
        is Py.With -> {
            this.items.forEach { it.contextExpr.gatherImports(sink, exclude) }
            this.forEachChildStmt { it.gatherImports(sink, exclude) }
        }
        is Py.Try -> {
            this.handlers.forEach { it.type.gatherImports(sink, exclude) }
            this.forEachChildStmt { it.gatherImports(sink, exclude) }
        }
        is Py.Raise -> {
            this.cause.gatherImports(sink, exclude)
            this.exc.gatherImports(sink, exclude)
        }
        is Py.Assert -> {
            msg.gatherImports(sink, exclude)
            test.gatherImports(sink, exclude)
        }
        is Py.ExprStmt -> this.value.gatherImports(sink, exclude)
        else -> {}
    }
}

private fun Py.Decorator.gatherImports(sink: ImportSink, exclude: Set<OutName>) {
    args.forEach { it.gatherImports(sink, exclude) }
    name.firstOrNull()?.let {
        sink(it, exclude)
    }
}

private fun Py.CallArg.gatherImports(sink: ImportSink, exclude: Set<OutName>) {
    value.gatherImports(sink, exclude)
}

private fun Py.Arguments.gatherImports(sink: ImportSink, exclude: Set<OutName>) {
    args.forEach { it.gatherImports(sink, exclude) }
}

private fun Py.Arg.gatherImports(sink: ImportSink, exclude: Set<OutName>) {
    defaultValue.gatherImports(sink, exclude)
}

private fun Py.Arguments.gatherNames(names: MutableSet<OutName>) {
    args.forEach { arg ->
        names.add(arg.arg.outName)
    }
}

/**
 * Python expressions can be on the left-hand side.
 */
private fun Py.Expr?.gatherImports(sink: ImportSink, exclude: Set<OutName>) {
    /**
     * The logic is the same for all comprehensions. Each comprehension can introduce formals, and we're not going to
     * get into the precise ordering of when they do.
     */
    fun doComprehension(generators: List<Py.Comprehension>, vararg exprs: Py.Expr) {
        val inNames = HashSet(exclude)
        generators.forEach {
            it.target.gatherNames(inNames)
        }
        generators.forEach {
            it.iter.gatherImports(sink, inNames)
            it.ifs.forEach { compIf -> compIf.test.gatherImports(sink, inNames) }
        }
        exprs.forEach {
            it.gatherImports(sink, inNames)
        }
    }

    when (this) {
        null -> {}
        is Py.Lambda -> {
            this.args?.gatherImports(sink, exclude)
            val inNames = HashSet(exclude)
            this.args?.gatherNames(inNames)
            this.body.gatherImports(sink, inNames)
        }
        is Py.ListComp -> doComprehension(this.generators, this.elt)
        is Py.SetComp -> doComprehension(this.generators, this.elt)
        is Py.DictComp -> doComprehension(this.generators, this.key, this.value)
        is Py.GeneratorComp -> doComprehension(this.generators, this.elt)
        is Py.Name -> sink(this, exclude)
        else -> forEachChildExpr { it.gatherImports(sink, exclude) }
    }
}

/** Gather names in a left-hand side expression. */
private fun Py.Expr?.gatherNames(names: MutableSet<OutName>) {
    when (this) {
        is Py.Attribute -> this.value.gatherNames(names)
        is Py.Subscript -> this.value.gatherNames(names)
        is Py.Starred -> this.value.gatherNames(names)
        is Py.Name -> names.add(this.outName)
        is Py.Tuple -> this.elts.forEach { elt -> elt.gatherNames(names) }
        else -> {}
    }
}
