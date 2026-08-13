package lang.temper.be.cpp

import lang.temper.ast.deepCopy
import lang.temper.be.tmpl.TmpL
import lang.temper.common.OCTAL_RADIX
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.CoreCodeLocation
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.ResolvedName

private val cppReservedNamespaces = setOf("std", "chrono", "filesystem")

internal fun safeCppNamespace(name: String): String =
    if (name in cppReservedNamespaces) "temper_$name" else name

fun Cpp.Expr.withComment(text: String, prefix: Boolean = true): Cpp.Expr = if (prefix) {
    Cpp.PreComment(pos, Cpp.Raw(pos, text), this)
} else {
    Cpp.PostComment(pos, this, Cpp.Raw(pos, text))
}

class CppBuilder(
    private val cppNames: CppNames,
    private val libraryRootToOutputDir: Map<FilePath, String> = emptyMap(),
) {
    var pos = unknownPos

    inline fun <Ret> pos(nextPos: Position, block: () -> Ret): Ret {
        val initPos = pos
        pos = nextPos
        try {
            return block()
        } finally {
            pos = initPos
        }
    }

    inline fun <Ret> pos(tree: TmpL.Tree, block: () -> Ret): Ret {
        return pos(tree.pos, block)
    }

    // the basics
    fun program(stmts: Iterable<Cpp.Global>): Cpp.Program =
        Cpp.Program(pos, stmts.map { it.deepCopy() })

    fun define(name: Cpp.SingleName, value: Cpp.Expr? = null): Cpp.Define =
        Cpp.Define(pos, name.deepCopy(), value?.deepCopy())
    fun undef(name: Cpp.SingleName): Cpp.Undef =
        Cpp.Undef(pos, name.deepCopy())
    fun pragma(text: Cpp.Raw): Cpp.Pragma =
        Cpp.Pragma(pos, text.deepCopy())
    fun includeGuard(name: Cpp.SingleName, program: Cpp.Program): Cpp.IncludeGuard =
        Cpp.IncludeGuard(pos, name.deepCopy(), program.deepCopy())

    fun ifPreProc(
        cond: Cpp.Expr,
        ifTrue: Cpp.Program,
        elifs: Iterable<Cpp.ElifPreProc> = listOf(),
        ifFalse: Cpp.Program? = null,
    ): Cpp.IfPreProc =
        Cpp.IfPreProc(
            pos,
            cond = cond.deepCopy(),
            ifTrue = ifTrue.deepCopy(),
            elifs = elifs.deepCopy(),
            ifFalse = ifFalse?.deepCopy(),
        )
    fun elifPreProc(cond: Cpp.Expr, program: Cpp.Program): Cpp.ElifPreProc =
        Cpp.ElifPreProc(pos, cond.deepCopy(), program.deepCopy())

    fun structDecl(name: Cpp.SingleName): Cpp.StructDecl =
        Cpp.StructDecl(pos, name.deepCopy())
    fun structDef(name: Cpp.SingleName, fields: Iterable<Cpp.StructPart>): Cpp.StructDef =
        Cpp.StructDef(pos, name.deepCopy(), fields.deepCopy())
    fun structField(type: Cpp.Type, name: Cpp.SingleName): Cpp.StructField =
        Cpp.StructField(pos, type.deepCopy(), name.deepCopy())
    fun derivedStructDef(
        name: Cpp.SingleName,
        bases: Iterable<Cpp.BaseSpec>,
        fields: Iterable<Cpp.StructPart>,
    ): Cpp.DerivedStructDef =
        Cpp.DerivedStructDef(pos, name.deepCopy(), bases.deepCopy(), fields.deepCopy())
    fun baseSpec(virtual: Boolean, base: Cpp.Type): Cpp.BaseSpec =
        Cpp.BaseSpec(pos, virt = if (virtual) Cpp.VirtualMod.Virtual else null, base = base.deepCopy())
    fun templateStructDef(
        typeParams: Iterable<Cpp.FuncParam>,
        def: Cpp.AnyStructDef,
    ): Cpp.TemplateStructDef =
        Cpp.TemplateStructDef(pos, typeParams.deepCopy(), def.deepCopy())
    fun templateFuncDef(
        typeParams: Iterable<Cpp.FuncParam>,
        def: Cpp.FuncDef,
    ): Cpp.TemplateFuncDef =
        Cpp.TemplateFuncDef(pos, typeParams.deepCopy(), def.deepCopy())
    fun templateFuncDecl(
        typeParams: Iterable<Cpp.FuncParam>,
        decl: Cpp.FuncDecl,
    ): Cpp.TemplateFuncDecl =
        Cpp.TemplateFuncDecl(pos, typeParams.deepCopy(), decl.deepCopy())

    fun funcDecl(
        ret: Cpp.Type?,
        convention: Cpp.SingleName?,
        name: Cpp.SingleName,
        args: Iterable<Cpp.Type>,
        qual: Cpp.MethodQualifier? = null,
    ): Cpp.FuncDecl =
        Cpp.FuncDecl(
            pos,
            ret = ret?.deepCopy(),
            convention = convention?.deepCopy(),
            name = name.deepCopy(),
            args = args.deepCopy(),
            qual = qual,
        )
    fun funcDecl(
        mod: Cpp.DefMod?,
        ret: Cpp.Type?,
        name: Cpp.SingleName,
        args: Iterable<Cpp.Type>,
    ): Cpp.FuncDecl =
        Cpp.FuncDecl(
            pos,
            mod = mod,
            ret = ret?.deepCopy(),
            name = name.deepCopy(),
            args = args.deepCopy(),
        )
    fun funcDef(
        attr: Cpp.SingleName?,
        mod: Cpp.DefMod?,
        ret: Cpp.Type?,
        convention: Cpp.SingleName?,
        name: Cpp.Name,
        args: Iterable<Cpp.FuncParam>,
        body: Cpp.BlockStmt,
        qual: Cpp.MethodQualifier? = null,
    ): Cpp.FuncDef =
        Cpp.FuncDef(
            pos,
            attr = attr,
            mod = mod,
            ret = ret?.deepCopy(),
            convention = convention?.deepCopy(),
            name = name.deepCopy(),
            args = args.deepCopy(),
            qual = qual,
            body = body.deepCopy(),
        )
    fun funcDef(
        mod: Cpp.DefMod?,
        ret: Cpp.Type?,
        name: Cpp.Name,
        args: Iterable<Cpp.FuncParam>,
        body: Cpp.BlockStmt,
    ): Cpp.FuncDef =
        Cpp.FuncDef(
            pos,
            mod = mod,
            ret = ret?.deepCopy(),
            convention = null,
            name = name.deepCopy(),
            args = args.deepCopy(),
            body = body.deepCopy(),
        )
    fun funcDef(ret: Cpp.Type?, name: Cpp.Name, args: Iterable<Cpp.FuncParam>, body: Cpp.BlockStmt): Cpp.FuncDef =
        funcDef(attr = null, mod = null, ret, convention = null, name, args, body)
    fun funcParam(type: Cpp.Type, name: Cpp.SingleName): Cpp.FuncParam =
        Cpp.FuncParam(pos, type.deepCopy(), name.deepCopy())

    fun varDecl(type: Cpp.Type, name: Cpp.SingleName): Cpp.VarDecl =
        Cpp.VarDecl(pos, type = type.deepCopy(), name = name.deepCopy())
    fun varDef(mod: Cpp.DefMod?, type: Cpp.Type, name: Cpp.SingleName, init: Cpp.Expr? = null): Cpp.VarDef =
        Cpp.VarDef(pos, mod, type.deepCopy(), name.deepCopy(), init?.deepCopy())
    fun varDef(type: Cpp.Type, name: Cpp.SingleName, init: Cpp.Expr? = null): Cpp.VarDef =
        varDef(mod = null, type, name, init = init)

    fun const(type: Cpp.Type): Cpp.ConstType =
        Cpp.ConstType(pos, type.deepCopy())
    fun ptr(type: Cpp.Type): Cpp.PtrType =
        Cpp.PtrType(pos, type.deepCopy())
    fun template(type: Cpp.Type, args: Iterable<Cpp.Type>): Cpp.TemplateType =
        Cpp.TemplateType(pos, type.deepCopy(), args.deepCopy())

    fun stmt(stmts: Collection<Cpp.Stmt>): Cpp.Stmt = when (stmts.size) {
        1 -> stmts.first().deepCopy()
        else -> Cpp.BlockStmt(pos, stmts.deepCopy())
    }

    fun blockStmt(stmts: Iterable<Cpp.Stmt>): Cpp.BlockStmt =
        Cpp.BlockStmt(pos, stmts.deepCopy())
    fun exprStmt(expr: Cpp.Expr): Cpp.ExprStmt =
        Cpp.ExprStmt(pos, expr.deepCopy())
    fun labelStmt(label: Cpp.SingleName, stmt: Cpp.Stmt): Cpp.LabelStmt =
        Cpp.LabelStmt(pos, label, stmt.deepCopy())
    fun gotoStmt(label: Cpp.SingleName): Cpp.GotoStmt =
        Cpp.GotoStmt(pos, label.deepCopy())
    fun returnStmt(value: Cpp.Expr? = null): Cpp.ReturnStmt =
        Cpp.ReturnStmt(pos, value?.deepCopy())
    fun throwStmt(value: Cpp.Expr): Cpp.ThrowStmt =
        Cpp.ThrowStmt(pos, value.deepCopy())
    fun tryCatch(tryBody: Cpp.Stmt, catchBody: Cpp.Stmt): Cpp.TryCatchStmt =
        Cpp.TryCatchStmt(pos, tryBody.deepCopy(), catchBody.deepCopy())
    fun breakStmt(): Cpp.BreakStmt =
        Cpp.BreakStmt(pos)
    fun switchStmt(subject: Cpp.Expr, cases: Iterable<Cpp.SwitchCase>, defaultBody: Cpp.Stmt): Cpp.SwitchStmt =
        Cpp.SwitchStmt(pos, subject.deepCopy(), cases.deepCopy(), defaultBody.deepCopy())
    fun switchCase(labels: Iterable<Cpp.CaseLabel>, body: Cpp.Stmt): Cpp.SwitchCase =
        Cpp.SwitchCase(pos, labels.deepCopy(), body.deepCopy())
    fun caseLabel(value: Cpp.Expr): Cpp.CaseLabel =
        Cpp.CaseLabel(pos, value.deepCopy())
    fun lambda(
        captures: Iterable<Cpp.LambdaCapture>,
        params: Iterable<Cpp.FuncParam>,
        mutable: Boolean,
        ret: Cpp.Type,
        body: Cpp.BlockStmt,
    ): Cpp.LambdaExpr =
        Cpp.LambdaExpr(
            pos,
            captures.deepCopy(),
            params.deepCopy(),
            mut = if (mutable) Cpp.LambdaMod.Mutable else null,
            ret = ret.deepCopy(),
            body = body.deepCopy(),
        )
    fun lambdaCapture(name: Cpp.SingleName): Cpp.LambdaCapture =
        Cpp.LambdaCapture(pos, name.deepCopy())

    fun indexExpr(base: Cpp.Expr, index: Cpp.Expr): Cpp.IndexExpr =
        Cpp.IndexExpr(pos, base.deepCopy(), index.deepCopy())
    fun callExpr(expr: Cpp.Expr, args: Iterable<Cpp.Expr>): Cpp.CallExpr =
        Cpp.CallExpr(pos, expr.deepCopy(), args.deepCopy())
    fun memberExpr(base: Cpp.Expr, name: Cpp.SingleName): Cpp.MemberExpr =
        Cpp.MemberExpr(pos, base.deepCopy(), name.deepCopy())

    fun unaryExpr(op: Cpp.UnaryOp, right: Cpp.Expr): Cpp.UnaryExpr =
        Cpp.UnaryExpr(pos, op.deepCopy(), right.deepCopy())

    fun binaryExpr(left: Cpp.Expr, op: Cpp.BinaryOp, right: Cpp.Expr): Cpp.BinaryExpr =
        Cpp.BinaryExpr(pos, left.deepCopy(), op.deepCopy(), right.deepCopy())

    fun literal(value: Cpp.Raw): Cpp.LiteralExpr =
        Cpp.LiteralExpr(pos, value.deepCopy())

    fun comment(text: Cpp.Raw): Cpp.Comment =
        Cpp.Comment(pos, text.deepCopy())

    fun raw(text: String): Cpp.Raw =
        Cpp.Raw(pos, text)

    fun singleName(name: CppName): Cpp.SingleName =
        Cpp.SingleName(pos, name)
    fun singleName(name: String): Cpp.SingleName =
        Cpp.SingleName(pos, CppName(name))

    fun scopedName(base: Cpp.Type, member: Cpp.SingleName): Cpp.ScopedName =
        Cpp.ScopedName(pos, base.deepCopy(), member.deepCopy())

    fun cast(type: Cpp.Type, expr: Cpp.Expr): Cpp.CastExpr =
        Cpp.CastExpr(pos, type.deepCopy(), expr.deepCopy())

    fun ifStmt(cond: Cpp.Expr, ifTrue: Cpp.Stmt, ifFalse: Cpp.Stmt? = null): Cpp.IfStmt =
        Cpp.IfStmt(pos, cond.deepCopy(), ifTrue.deepCopy(), ifFalse?.deepCopy())

    fun whileStmt(cond: Cpp.Expr, body: Cpp.Stmt): Cpp.WhileStmt =
        Cpp.WhileStmt(pos, cond.deepCopy(), body.deepCopy())

    fun namespace(name: Cpp.SingleName?, body: Iterable<Cpp.Global>): Cpp.Namespace =
        Cpp.Namespace(pos, name, body.deepCopy())

    fun include(name: String): Cpp.Include =
        Cpp.Include(pos, Cpp.Raw(pos, name))

    fun thisExpr(): Cpp.ThisExpr = Cpp.ThisExpr(pos)

    // helpers
    data class Struct(
        val decl: Cpp.StructDecl,
        val def: Cpp.StructDef,
    )

    fun struct(name: Cpp.SingleName, fields: Iterable<Cpp.StructPart>): Struct = Struct(
        decl = structDecl(name),
        def = structDef(name, fields),
    )

    data class Func(val decl: Cpp.FuncDecl, val def: Cpp.FuncDef)

    fun func(
        name: Cpp.Name,
        retType: Cpp.Type?,
        convention: Cpp.SingleName?,
        argTypes: Iterable<Cpp.Type>,
        argNames: Iterable<Cpp.SingleName>,
        block: Cpp.BlockStmt,
        qual: Cpp.MethodQualifier? = null,
    ): Func {
        val funcName = when (name) {
            is Cpp.SingleName -> name
            is Cpp.ScopedName -> name.member
        }
        return Func(
            decl = funcDecl(
                retType,
                convention = convention,
                funcName,
                argTypes,
                qual = qual,
            ),
            def = funcDef(
                attr = null,
                mod = null,
                retType,
                convention = convention,
                name,
                argTypes.zip(argNames).map {
                    funcParam(it.first, it.second)
                },
                block,
                qual = qual,
            ),
        )
    }

    fun func(
        name: Cpp.Name,
        retType: Cpp.Type,
        argTypes: Iterable<Cpp.Type>,
        argNames: Iterable<Cpp.SingleName>,
        block: Cpp.BlockStmt,
        qual: Cpp.MethodQualifier? = null,
    ): Func = func(
        name,
        retType,
        convention = null,
        argTypes = argTypes,
        argNames = argNames,
        block = block,
        qual = qual,
    )

    fun func(
        name: Cpp.Name,
        retType: Cpp.Type,
        argTypes: Iterable<Pair<Cpp.Type, Cpp.SingleName>>,
        block: Cpp.BlockStmt,
        qual: Cpp.MethodQualifier? = null,
    ): Func = func(
        name,
        retType,
        argTypes.map { it.first },
        argTypes.map { it.second },
        block,
        qual = qual,
    )

    // shorthand
    fun callExpr(func: Cpp.Expr, vararg args: Cpp.Expr): Cpp.CallExpr =
        callExpr(func, args.toList())

    fun namespace(body: Iterable<Cpp.Global>): Cpp.Namespace =
        Cpp.Namespace(pos, null, body.deepCopy())

    fun template(base: Cpp.Type, vararg params: Cpp.Type): Cpp.TemplateType =
        template(base, params.toList())

    fun ifStmt(cond: Cpp.Expr, then: Cpp.Stmt): Cpp.IfStmt =
        ifStmt(cond, then, null)

    fun tmp(name: String = "tmp"): Cpp.SingleName =
        singleName(cppNames.tmp(name))

    fun literal(value: Boolean): Cpp.LiteralExpr =
        if (value) {
            literal(raw("true"))
        } else {
            literal(raw("false"))
        }

    fun literal(value: Number): Cpp.LiteralExpr = literal(raw(value.toString()))

    fun literal(value: String): Cpp.LiteralExpr {
        // Encode the whole string as UTF-8 bytes for correct multi-byte character handling
        val utf8Bytes = value.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder()
        var hasNullByte = false
        for (b in utf8Bytes) {
            @Suppress("MagicNumber") // 0xFF mask for unsigned byte conversion
            val unsigned = b.toInt() and 0xFF
            if (unsigned == 0) hasNullByte = true
            val ch = unsigned.toChar()
            val escaped = escapeCodes[ch]
            if (escaped != null) {
                sb.append(escaped)
            } else if (ch in ' '..'~') {
                sb.append(ch)
            } else {
                sb.append("\\${unsigned.toString(OCTAL_RADIX).padStart(OCTAL_ESCAPE_DIGITS, '0')}")
            }
        }
        // Use std::string(data, length) constructor for strings with embedded null bytes
        // since const char* constructor stops at the first \0
        return if (hasNullByte) {
            literal(raw("std::string(\"$sb\", ${utf8Bytes.size})"))
        } else {
            literal(raw("\"$sb\""))
        }
    }

    fun name(resolvedName: ResolvedName): Cpp.SingleName = singleName(cppNames.name(resolvedName))

    fun name(id: TmpL.Id): Cpp.SingleName = singleName(cppNames.name(id))

    fun name(base: Cpp.Name, member: Cpp.SingleName): Cpp.ScopedName = scopedName(base, member)

    fun name(arg: Iterable<String>): Cpp.Name = arg.flatMap {
        it.split("::")
    }.let { parts ->
        parts.drop(1).fold(
            singleName(CppName(parts.first())) as Cpp.Name,
        ) { acc, elem ->
            scopedName(acc, singleName(CppName(elem)))
        }
    }

    fun name(vararg parts: String): Cpp.Name = name(parts.filter { it.isNotEmpty() }.toList())

    fun libraryName(text: String): Cpp.SingleName {
        val base = cppNames.library(text).text
        return singleName(CppName(safeCppNamespace(base)))
    }

    fun comment(text: String): Cpp.Comment = comment(raw(text))

    fun pragma(text: String): Cpp.Pragma = pragma(raw(text))
    fun pragma(vararg parts: String): Cpp.Pragma = pragma(parts.joinToString(" "))

    fun unaryOp(name: String): Cpp.UnaryOp = when (name) {
        "++" -> Cpp.UnaryOp(pos, UnaryOpEnum.PreInc)
        "--" -> Cpp.UnaryOp(pos, UnaryOpEnum.PreDec)
        "+" -> Cpp.UnaryOp(pos, UnaryOpEnum.Plus)
        "-" -> Cpp.UnaryOp(pos, UnaryOpEnum.Minus)
        "!" -> Cpp.UnaryOp(pos, UnaryOpEnum.Not)
        "~" -> Cpp.UnaryOp(pos, UnaryOpEnum.BitNot)
        "*" -> Cpp.UnaryOp(pos, UnaryOpEnum.Deref)
        "&" -> Cpp.UnaryOp(pos, UnaryOpEnum.AddrOf)
        else -> error("unsupported C++ unary operator: $name")
    }

    fun binaryOp(name: String): Cpp.BinaryOp = when (name) {
        "=" -> Cpp.BinaryOp(pos, BinaryOpEnum.Assign)
        "+" -> Cpp.BinaryOp(pos, BinaryOpEnum.Add)
        "-" -> Cpp.BinaryOp(pos, BinaryOpEnum.Sub)
        "*" -> Cpp.BinaryOp(pos, BinaryOpEnum.Mul)
        "/" -> Cpp.BinaryOp(pos, BinaryOpEnum.Div)
        "%" -> Cpp.BinaryOp(pos, BinaryOpEnum.Mod)
        "&" -> Cpp.BinaryOp(pos, BinaryOpEnum.BitAnd)
        "|" -> Cpp.BinaryOp(pos, BinaryOpEnum.BitOr)
        "^" -> Cpp.BinaryOp(pos, BinaryOpEnum.BitXor)
        "<<" -> Cpp.BinaryOp(pos, BinaryOpEnum.Shl)
        ">>" -> Cpp.BinaryOp(pos, BinaryOpEnum.Shr)
        "+=" -> Cpp.BinaryOp(pos, BinaryOpEnum.AddAssign)
        "-=" -> Cpp.BinaryOp(pos, BinaryOpEnum.SubAssign)
        "*=" -> Cpp.BinaryOp(pos, BinaryOpEnum.MulAssign)
        "/=" -> Cpp.BinaryOp(pos, BinaryOpEnum.DivAssign)
        "%=" -> Cpp.BinaryOp(pos, BinaryOpEnum.ModAssign)
        "<<=" -> Cpp.BinaryOp(pos, BinaryOpEnum.ShlAssign)
        ">>=" -> Cpp.BinaryOp(pos, BinaryOpEnum.ShrAssign)
        "&=" -> Cpp.BinaryOp(pos, BinaryOpEnum.AndAssign)
        "^=" -> Cpp.BinaryOp(pos, BinaryOpEnum.XorAssign)
        "|=" -> Cpp.BinaryOp(pos, BinaryOpEnum.OrAssign)
        "==" -> Cpp.BinaryOp(pos, BinaryOpEnum.Eq)
        "!=" -> Cpp.BinaryOp(pos, BinaryOpEnum.Ne)
        "<" -> Cpp.BinaryOp(pos, BinaryOpEnum.Lt)
        ">" -> Cpp.BinaryOp(pos, BinaryOpEnum.Gt)
        "<=" -> Cpp.BinaryOp(pos, BinaryOpEnum.Le)
        ">=" -> Cpp.BinaryOp(pos, BinaryOpEnum.Ge)
        "&&" -> Cpp.BinaryOp(pos, BinaryOpEnum.And)
        "||" -> Cpp.BinaryOp(pos, BinaryOpEnum.Or)
        "." -> Cpp.BinaryOp(pos, BinaryOpEnum.Dot)
        "->" -> Cpp.BinaryOp(pos, BinaryOpEnum.Arrow)
        else -> error("unsupported C++ binary operator: $name")
    }

    fun op(op: String, parts: List<Cpp.Expr>): Cpp.Expr {
        if (parts.size == 1) {
            return unaryExpr(unaryOp(op), parts[0])
        } else {
            var run = parts[0]
            for (part in parts.drop(1)) {
                run = binaryExpr(run, binaryOp(op), part)
            }
            return run
        }
    }

    fun op(op: String, vararg parts: Cpp.Expr): Cpp.Expr = op(op, parts.toList())

    private val byLibraryRoot = mutableMapOf<FilePath, String>()

    private val libraryNameRegex = Regex("[^a-zA-Z_]+")

    fun nameTextForModule(library: ModuleName): String = byLibraryRoot.getOrPut(library.libraryRoot()) {
        // Use the library name from configuration if available, otherwise fall back
        // to sanitizing the raw library root path segments.
        val libRoot = library.libraryRoot()
        var exist = libraryRootToOutputDir[libRoot]?.let { name ->
            libraryNameRegex.replace(name, "_")
        } ?: libraryNameRegex.replace(
            libRoot.segments.joinToString("_"),
            "_",
        )
        if (exist.isEmpty()) {
            exist = "mod_${byLibraryRoot.size}"
        }
        safeCppNamespace(exist)
    }

    fun nameForModule(library: ModuleLocation): Cpp.Name = when (library) {
        CoreCodeLocation -> name(TEMPER_CORE_NAMESPACE)
        is ModuleName -> {
            // Use only the library root namespace, not sub-module paths.
            // All modules in a library share the same C++ namespace.
            val lib = nameTextForModule(library)
            name(lib)
        }
    }

    /** Compute the include path for a module's header file. */
    fun includePathForModule(library: ModuleName): String {
        val libRootPath = library.libraryRoot()
        // Use the library name from configuration if available, otherwise
        // fall back to joining the raw source path segments.
        val libRoot = libraryRootToOutputDir[libRootPath]
            ?: libRootPath.segments.joinToString("/") { it.baseName }
        val relPath = library.relativePath()
        return when {
            relPath.segments.isEmpty() -> "$libRoot/$INIT_NAME$HPP_EXT"
            else -> {
                // Preserve subdirectory structure: for a module at backend/x86,
                // produce "tempercc/backend/x86.hpp" not "tempercc/x86.hpp".
                val relParts = relPath.segments.joinToString("/") { it.baseName }
                "$libRoot/$relParts$HPP_EXT"
            }
        }
    }

    // mini-parsers
    private val typeNameRegex = Regex("^[a-zA-Z_][a-zA-Z_0-9]*")

    fun type(text: String): Cpp.Type {
        if (text.startsWith(" ")) {
            return type(text.trimStart())
        }
        if (text.startsWith("const")) {
            return const(type(text.removePrefix("const")))
        }
        var remain = text

        fun readName(): CppName? {
            val match = typeNameRegex.find(remain) ?: return null
            remain = remain.removePrefix(match.value).trimStart()
            return CppName(match.value, allowKey = true)
        }

        var type: Cpp.Type = singleName(readName() ?: error("invalid C++ type: '$text'"))

        fun handleTemplates(): Boolean {
            if (remain.startsWith("<")) {
                remain = remain.removePrefix("<").trimStart()
                val typeArgs = mutableListOf<Cpp.Type>()
                var curString = ""
                var depth = 1
                while (depth != 0) {
                    check(remain.isNotEmpty())
                    remain = when {
                        remain.startsWith(" ") -> {
                            curString += " "
                            remain.trimStart()
                        }

                        remain.startsWith("<") -> {
                            curString += "<"
                            depth += 1
                            remain.removePrefix("<")
                        }

                        remain.startsWith(">") -> {
                            depth -= 1
                            if (depth != 0) {
                                curString += ">"
                            }
                            remain.removePrefix(">")
                        }

                        depth == 1 && remain.startsWith(",") -> {
                            typeArgs.add(type(curString))
                            curString = ""
                            remain.removePrefix(",")
                        }

                        else -> {
                            curString += remain[0]
                            remain.drop(1)
                        }
                    }
                }
                if (curString.isNotEmpty()) {
                    typeArgs.add(type(curString))
                }
                type = template(type, typeArgs)

                return true
            }

            return false
        }

        do {
            while (remain.startsWith("::")) {
                remain = remain.removePrefix("::").trimStart()
                type = scopedName(type, singleName(readName() ?: error("expected name after '::' in type: '$text'")))
            }
        } while (handleTemplates())

        while (remain.isNotEmpty()) {
            type = when {
                remain.startsWith(" ") -> {
                    remain = remain.trimStart()
                    type
                }
                remain.startsWith("*") -> {
                    remain = remain.removePrefix("*").trimStart()
                    ptr(type)
                }
                remain.startsWith("const") -> {
                    remain = remain.removePrefix("const").trimStart()
                    const(type)
                }
                else -> break
            }
        }

        check(remain.trimStart().isEmpty())

        return type
    }

    companion object {
        /** Width of an octal byte escape (`\NNN`): a byte's max octal value 377 is 3 digits. */
        private const val OCTAL_ESCAPE_DIGITS = 3

        private val escapeCodes = mapOf<Char, String>(
            7.toChar() to "\\a",
            8.toChar() to "\\b",
            9.toChar() to "\\t",
            11.toChar() to "\\v",
            10.toChar() to "\\n",
            13.toChar() to "\\r",
            34.toChar() to "\\\"",
            39.toChar() to "\\'",
            92.toChar() to "\\\\",
        )
    }
}
