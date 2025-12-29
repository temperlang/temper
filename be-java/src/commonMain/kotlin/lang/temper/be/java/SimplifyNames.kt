package lang.temper.be.java

import lang.temper.ast.OutTree
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.OutName
import lang.temper.be.java.Java as J

/**
 * Create an instance and run it against this value.
 *
 * SIDE EFFECT: Alters the original value.
 */
fun simplifyNames(program: J.Program): J.Program {
    if (program is J.TopLevelClassDeclaration) {
        SimplifyNames(program).scanAndReplace()
    }
    return program
}

/**
 * Create a test wrapper and simplify, returning the simplified expression.
 *
 * SIDE EFFECT: Alters the original value.
 */
fun simplifyNames(expr: J.Expression): J.Expression {
    val rig = testRig(expr)
    SimplifyNames(rig).scanAndReplace()
    return expr
}

/**
 * Create a test wrapper and simplify, returning the simplified statement.
 *
 * SIDE EFFECT: Alters the original value.
 */
fun simplifyNames(stmt: J.BlockLevelStatement): J.BlockLevelStatement {
    val rig = testRig(stmt)
    SimplifyNames(rig).scanAndReplace()
    return stmt
}

/** A class to simplify fully qualified names in place by adding import statements. */
class SimplifyNames(private val top: J.TopLevelClassDeclaration) {
    private val importables = mutableListOf<Importable>()
    private val topLevel: Scope = Scope()
    private val implicits = buildList {
        add(javaLang)
        val pkgName = top.packageStatement?.packageName ?.let { QualifiedName.fromAst(it) }
        if (pkgName != null) {
            add(pkgName)
        }
        for (klass in top.walk()) {
            if (klass is J.ClassOrInterfaceDeclaration) {
                var node: OutTree<J.Tree>? = klass
                val parts = mutableListOf<OutName>()
                while (node != null) {
                    if (node is J.ClassOrInterfaceDeclaration) {
                        parts.add(node.name.outName)
                    }
                    node = node.parent
                }
                parts.reverse()
                add(pkgName.concat(parts))
                // All nested types are always visible from the top scope.
                topLevel.addType(klass.name.outName)
            }
        }
    }

    fun scanAndReplace() {
        topLevel.scanClass(top.classDef)

        // Group the imports by the class of import and name being imported; this generates batches of imports
        val grouped = importables.groupBy { it.cat to it.fullName }.values
        // Then sort that list of batches of imports ascending by the approximate characters added by the change.
        val imports = grouped.sortedBy { imps ->
            imps.first().importStmtEstimate() + imps.sumOf { it.siteReplaceEstimate() }
        }
        // The new import statements to be added to the heading.
        val importBlock = mutableListOf<J.ImportStatement>()
        val neededNames = top.programMeta.neededNames.toMutableSet()
        for (batch in imports) {
            val first = batch.first()
            // It would be nice to get just package names, but we don't track that very well.
            neededNames.add(first.fullName)
            // As we import, we're changing the global scope; foo.bar.Qux rules out importing bar.foo.Qux.
            // But allow implicits if the one we're already tracking is implicit, as it could be the same thing or
            // something else we've already worked out.
            val availability = topLevel.availability(first.cat, first.importedName)
            if (availability != null && !(availability == Availability.Implicit && first.implicit)) {
                continue
            }
            // Apparently no conflict.
            topLevel.add(first.cat, first.importedName, first.implicit.implicitToAvailability())
            if (!implicits.any { first.fullName.isChildOf(it) }) {
                importBlock.add(first.importStatement(top.pos))
            }
            for (imp in batch) {
                imp.updateInPlace()
            }
        }
        top.imports += importBlock
        top.programMeta.neededNames = neededNames
    }

    private fun Scope.scanClass(cd: J.ClassOrInterfaceDeclaration) {
        // addType(cd.name.outName)  // not strictly necessary
        val scope = inner()
        when (cd) {
            is J.ClassDeclaration -> {
                scope.scanTypeParams(cd.params)
                scope.importAnnotations(cd.anns)
                cd.classImplements.forEach { scope.scanType(it) }
                scope.scanType(cd.classExtends)
                cd.permits.forEach { scope.scanType(it) }
                cd.body.forEach { scope.scanClassMember(it) }
            }

            is J.InterfaceDeclaration -> {
                scope.scanTypeParams(cd.params)
                scope.importAnnotations(cd.anns)
                cd.classExtends.forEach { scope.scanType(it) }
                cd.permits.forEach { scope.scanType(it) }
                cd.body.forEach { scope.scanInterfaceMember(it) }
            }
        }
    }

    private fun Scope.scanClassMember(m: J.ClassBodyDeclaration): Unit = when (m) {
        is J.ClassDeclaration -> scanClass(m)
        is J.CommentLine -> {}
        is J.ConstructorDeclaration -> {
            val scope = inner()
            for (p in m.parameters) {
                scope.addValue(p.name.outName)
                scanType(p.type)
            }
            m.exceptionTypes.forEach { scanType(it) }
            scope.scanBody(m.body)
        }

        is J.FieldDeclaration -> {
            for (x in m.variables) {
                addValue(x.variable.outName)
            }
            scanType(m.type)
            importAnnotations(m.anns)
        }

        is J.Initializer -> {
            scanBody(m.body, incorporated = false)
        }

        is J.InterfaceDeclaration -> scanClass(m)
        is J.MethodDeclaration -> {
            addMethod(m.name.outName)
            val scope = inner()
            scope.importAnnotations(m.anns)
            scope.scanType(m.result)
            scope.scanTypeParams(m.typeParams)
            for (p in m.parameters) {
                scope.addValue(p.name.outName)
                scope.scanType(p.type)
            }
            m.exceptionTypes.forEach { scope.scanType(it) }
            scope.scanBody(m.body)
        }
    }

    private fun Scope.scanInterfaceMember(m: J.InterfaceBodyDeclaration): Unit = when (m) {
        is J.ClassDeclaration -> scanClass(m)
        is J.CommentLine -> {}
        is J.InterfaceDeclaration -> scanClass(m)
        is J.InterfaceFieldDeclaration -> {
            scanType(m.type)
            for (x in m.variables) {
                addValue(x.variable.outName)
                scanExpr(x.initializer)
            }
        }

        is J.InterfaceMethodDeclaration -> {
            addMethod(m.name.outName)
            val scope = inner()
            scope.importAnnotations(m.anns)
            scope.scanType(m.result)
            scope.scanTypeParams(m.typeParams)
            for (p in m.parameters) {
                scope.addValue(p.name.outName)
                scope.scanType(p.type)
            }
            m.exceptionTypes.forEach { scope.scanType(it) }
            scope.scanBody(m.body)
        }
    }

    private fun Scope.importAnnotations(a: Iterable<J.Annotation>) = a.forEach {
        importType(it.name)
    }

    private fun Scope.scanType(t: J.ResultType?): Unit = when (t) {
        null -> {}
        is J.ArrayType -> scanType(t.type)
        is J.ClassType -> {
            scanTypeArguments(t.args)
            importType(t.type)
            importAnnotations(t.anns)
        }
        is J.PrimitiveType, is J.VoidType -> {}
    }

    /** Type parameters introduce new types, so these should be done first to avoid trying to import them. */
    private fun Scope.scanTypeParams(px: J.TypeParameters) {
        for (p in px.params) {
            addType(p.type.outName)
        }
        for (p in px.params) {
            importAnnotations(p.anns)
        }
    }

    /** Type arguments consume types. */
    private fun Scope.scanTypeArguments(ax: J.TypeArguments?) {
        ax?.args?.forEach { scanTypeArgument(it) }
    }

    private fun Scope.scanTypeArgument(a: J.TypeArgument) = when (a) {
        is J.ExtendsTypeArgument -> {
            scanTypeArguments(a.args)
            importType(a.type)
            importAnnotations(a.anns)
        }

        is J.ReferenceTypeArgument -> {
            scanTypeArguments(a.args)
            importType(a.annType)
            importAnnotations(a.annType.anns)
        }

        is J.SuperTypeArgument -> {
            scanTypeArguments(a.args)
            importType(a.type)
            importAnnotations(a.anns)
        }

        is J.WildcardTypeArgument -> {
            importAnnotations(a.anns)
        }
    }

    private fun Scope.scanBody(s: J.BlockLevelStatement?, incorporated: Boolean = true): Unit = when (s) {
        null -> {}
        is J.LocalVariableDeclaration -> {
            addValue(s.name.outName)
            scanType(s.type)
            scanExpr(s.expr)
        }

        is J.AlternateConstructorInvocation -> s.args.forEach { scanExpr(it.expr) }
        is J.AssertStatement -> {
            scanExpr(s.test)
            scanExpr(s.msg)
        }

        is J.BlockStatement -> {
            val scope = if (incorporated) this else inner()
            s.body.forEach { scope.scanBody(it, incorporated = false) }
        }

        is J.BreakStatement -> {}
        is J.ContinueStatement -> {}
        is J.DoStatement -> {
            val scope = inner()
            scope.scanExpr(s.test)
            scope.scanBody(s.body)
        }

        is J.EmptyStatement -> {}
        is J.ExpressionStatement -> scanExpr(s.expr)
        is J.IfStatement -> {
            var ii: J.ElseBlockStatement? = s
            while (ii != null) {
                ii = when (ii) {
                    is J.BlockStatement -> {
                        scanBody(ii, incorporated = false)
                        null
                    }
                    is J.IfStatement -> {
                        scanExpr(ii.test)
                        scanBody(ii.consequent, incorporated = false)
                        ii.alternate
                    }
                }
            }
        }

        is J.LabeledStatement -> scanBody(s.stmt)
        is J.ReturnStatement -> scanExpr(s.expr)
        is J.SwitchStatement -> {
            scanExpr(s.selector)
            when (val block = s.block) {
                is J.SwitchCaseBlock -> {
                    block.cases.forEach { case ->
                        scanSwitchLabel(case.label)
                        case.body.forEach { body -> scanBody(body) }
                    }
                }
                is J.SwitchRuleBlock -> {
                    block.rules.forEach { rule ->
                        scanSwitchLabel(rule.label)
                        when (rule) {
                            is J.BlockRuleStatement -> scanBody(rule.block)
                            is J.ExpressionRuleStatement -> scanExpr(rule.expr)
                            is J.ThrowRuleStatement -> scanExpr(rule.expr)
                        }
                    }
                }
            }
        }
        is J.ThrowStatement -> scanExpr(s.expr)
        is J.TryStatement -> {
            val scope = inner()
            for (r in s.resources) {
                TODO("$r not handled yet")
            }
            scope.scanBody(s.bodyBlock)
            for (cb in s.catchBlocks) {
                val catchScope = scope.inner()
                catchScope.addValue(cb.name.outName)
                cb.types.forEach { catchScope.scanType(it) }
                catchScope.scanBody(cb.body)
            }
            scope.scanBody(s.finallyBlock)
        }

        is J.WhileStatement -> {
            val scope = inner()
            scope.scanExpr(s.test)
            scope.scanBody(s.body)
        }

        is J.YieldStatement -> scanExpr(s.expr)
        is J.CommentLine -> {}
        is J.LocalClassDeclaration -> {
            addType(s.name.outName)
            val scope = inner()
            scope.scanTypeParams(s.params)
            scope.importAnnotations(s.anns)
            s.classImplements.forEach { scope.scanType(it) }
            scope.scanType(s.classExtends)
            s.body.forEach { scope.scanClassMember(it) }
        }
        is J.LocalInterfaceDeclaration -> {
            addType(s.name.outName)
            val scope = inner()
            scope.scanTypeParams(s.params)
            scope.importAnnotations(s.anns)
            s.classExtends.forEach { scope.scanType(it) }
            s.body.forEach { scope.scanInterfaceMember(it) }
        }
    }

    private fun Scope.scanSwitchLabel(label: J.SwitchLabel?): Unit = when (label) {
        null -> {}
        is J.SwitchCaseLabel -> label.cases.forEach { scanExpr(it) }
        is J.SwitchDefaultLabel -> {}
    }

    private fun Scope.scanExpr(e: J.Expression?): Unit = when (e) {
        is J.CastExpr -> {
            scanType(e.type)
            scanExpr(e.expr)
        }

        is J.ConstructorReferenceExpr -> importType(e.type)
        is J.AssignmentExpr -> {
            scanLhs(e.left)
            scanExpr(e.right)
        }

        is J.InstanceCreationExpr -> {
            scanType(e.type)
            scanTypeArguments(e.typeArgs)
            scanArgs(e.args)
        }

        is J.InstanceMethodInvocationExpr -> {
            scanExpr(e.expr)
            scanArgs(e.args)
            scanTypeArguments(e.typeArgs)
        }

        is J.InfixExpr -> {
            scanExpr(e.left)
            scanExpr(e.right)
        }

        is J.PostfixExpr -> scanExpr(e.expr)
        is J.PrefixExpr -> scanExpr(e.expr)
        is J.StaticMethodInvocationExpr -> {
            importType(e.type)
            scanArgs(e.args)
            scanTypeArguments(e.typeArgs)
        }

        is J.StaticMethodReferenceExpr -> importType(e.type)
        is J.StaticFieldAccessExpr -> importType(e.type)

        is J.FieldAccessExpr -> {
            scanExpr(e.expr)
        }

        is J.InstanceMethodReferenceExpr -> scanExpr(e.expr)
        is J.InstanceofExpr -> {
            scanExpr(e.left)
            scanType(e.right)
        }

        is J.LambdaExpr -> scanLambda(e)
        is J.NameExpr -> scanName(e)
        is J.SwitchExpr -> TODO("switch expressions not used yet")
        is J.ClassLiteral -> scanType(e.type)
        is J.BooleanLiteral,
        is J.CharacterLiteral,
        is J.FloatingPointLiteral,
        is J.IntegerLiteral,
        is J.NullLiteral,
        is J.StringLiteral,
        is J.ThisExpr,
        null,
        -> {
        }
    }

    private fun Scope.scanName(e: J.NameExpr) {
        importName(e)
    }

    private fun Scope.scanLhs(e: J.LeftHandSide) = when (e) {
        is J.FieldAccessExpr -> scanExpr(e.expr)
        is J.NameExpr -> scanName(e)
    }

    private fun Scope.scanArgs(ax: Iterable<J.Argument>) = ax.forEach { scanExpr(it.expr) }

    private fun Scope.scanLambda(e: J.LambdaExpr) {
        val scope = inner()
        when (val px = e.params) {
            is J.LambdaComplexParams -> {
                for (p in px.params) {
                    when (p) {
                        is J.LambdaParam -> {
                            scope.addValue(p.name.outName)
                            scanType(p.type)
                        }

                        is J.LambdaVarParam -> {
                            scope.addValue(p.name.outName)
                            scanType(p.type)
                        }
                    }
                }
            }

            is J.LambdaSimpleParams -> {
                for (p in px.params) {
                    scope.addValue(p.outName)
                }
            }
        }
        when (val b = e.body) {
            is J.Expression -> scope.scanExpr(b)
            is J.BlockStatement -> scope.scanBody(b)
        }
    }

    private fun checkType(scope: Scope, check: Importable) {
        // Don't bother importing single words.
        if (check.fullName.size <= 1) {
            return
        }
        if (implicits.any { check.fullName.isChildOf(it) }) {
            importables.add(check.implicit())
        }
        // Don't import if the imported name is in scope.
        if (scope.containsType(check.importedName)) {
            return
        }
        importables.add(check)
    }

    private fun Scope.importType(name: J.QualIdentifier?) {
        checkType(this, ReplaceQualIdentifier(name ?: return))
    }

    private fun Scope.importType(name: J.AnnotatedQualIdentifier) {
        checkType(this, ReplaceAnnQualIdentifier(name))
    }
    fun Scope.importName(name: J.NameExpr) {
        val nameIdent = name.ident
        when {
            nameIdent.size <= 1 -> return
            containsValue(nameIdent.first().outName) -> return
        }
        val check = ReplaceNameExpr(name)
        if (implicits.any { check.fullName.isChildOf(it) }) {
            importables.add(check.implicit())
        }
        // Don't import if the imported name is in scope.
        if (containsValue(check.importedName)) {
            return
        }
        importables.add(check)
    }
}

/** An "importable" is a name of some sort that may be updated in place given the appropriate import statement. */
abstract class Importable(val cat: Cat, val fullName: QualifiedName, val static: Boolean = false) {

    /** Modifies the AST in place to replace the full name with the shorter imported name. */
    abstract fun updateInPlace()

    /** The name after importing. */
    val importedName: OutName = fullName.lastPart
    private var _implicit: Boolean = false
    val implicit get() = _implicit

    /** Generates the import statement; this doesn't check whether an import is needed as that is contextual. */
    open fun importStatement(pos: Position = unknownPos): J.ImportStatement =
        if (static) {
            J.ImportStaticStatement(pos, fullName.toQualIdent(pos))
        } else {
            J.ImportClassStatement(pos, fullName.toQualIdent(pos))
        }

    /** Approx characters added by import statement */
    @Suppress("MagicNumber")
    fun importStmtEstimate(): Int = 8 + (if (static) 7 else 0) + fullName.estimateLength

    /** Approx characters added by replacement; should be negative. */
    fun siteReplaceEstimate(): Int = importedName.outputNameText.length - fullName.estimateLength

    /** Updates whether this replacement is implicit, that is, whether an import is needed. */
    fun implicit(): Importable {
        _implicit = true
        return this
    }
}

class ReplaceQualIdentifier(private val node: J.QualIdentifier) : Importable(Cat.TYPE, QualifiedName.fromAst(node)) {
    override fun updateInPlace() {
        node.ident = listOf(importedName.toIdentifier(node.pos))
    }
}

class ReplaceAnnQualIdentifier(private val node: J.AnnotatedQualIdentifier) :
    Importable(Cat.TYPE, QualifiedName.fromAst(node)) {
    override fun updateInPlace() {
        node.pkg = listOf()
        node.type.outName = importedName
    }
}

class ReplaceNameExpr(private val node: J.NameExpr) :
    Importable(Cat.TYPE, QualifiedName.fromAst(node), static = true) {
    override fun updateInPlace() {
        node.ident = listOf(importedName.toIdentifier(node.pos))
    }
}

/** Java has different categories of names that do not conflict in visibility. */
enum class Cat {
    TYPE,
    METHOD,
    VALUE,
}

typealias CatName = Pair<Cat, OutName>

/**
 * A visibility scope tracks whether various names are already defined, and thus shouldn't be imported.
 * Outside scopes are implicitly visible within an inner scope.
 */
class Scope(private val outer: Scope? = null) {

    /**
     * Track explicit vs implicit availability to assist knowing when we can simplify implicits.
     * TODO Better would be to track the qualified name of each, but that's a bit trickier at the moment.
     */
    private val entities = mutableMapOf<CatName, Availability>()

    private fun availability(pair: CatName): Availability? = entities[pair] ?: outer?.availability(pair)
    fun availability(cat: Cat, name: OutName): Availability? = availability(cat to name)

    private fun contains(pair: CatName): Boolean = availability(pair) != null
    fun contains(cat: Cat, name: OutName) = contains(cat to name)
    fun containsType(name: OutName): Boolean = contains(Cat.TYPE to name)
    fun containsValue(name: OutName): Boolean = contains(Cat.VALUE to name)

    fun addType(name: OutName) {
        entities[Cat.TYPE to name] = Availability.Implicit
    }
    fun addMethod(name: OutName) {
        entities[Cat.METHOD to name] = Availability.Implicit
    }
    fun addValue(name: OutName) {
        entities[Cat.VALUE to name] = Availability.Implicit
    }
    fun add(cat: Cat, name: OutName, availability: Availability) {
        entities[cat to name] = availability
    }

    fun inner() = Scope(outer = this)
}

enum class Availability {
    Explicit,
    Implicit,
}

fun Boolean.implicitToAvailability() = when {
    this -> Availability.Implicit
    else -> Availability.Explicit
}

private fun testRig(stmt: J.BlockLevelStatement): J.TopLevelClassDeclaration = J.TopLevelClassDeclaration(
    unknownPos,
    programMeta = J.ProgramMeta(unknownPos),
    classDef = J.ClassDeclaration(
        unknownPos,
        name = OutName("Test", null).toIdentifier(unknownPos),
        body = listOf(
            J.MethodDeclaration(
                unknownPos,
                name = OutName("test", null).toIdentifier(unknownPos),
                result = J.VoidType(
                    unknownPos,
                ),
                parameters = listOf(),
                body = stmt.asBlock(unknownPos),
            ),
        ),
    ),
)

private fun testRig(expr: J.Expression) =
    testRig(J.ExpressionStatement(unknownPos, expr.asExprStmtExpr(unknownPos)))
