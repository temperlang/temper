package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.Dependencies
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.mapParameters
import lang.temper.common.MimeType
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.resolveFile
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModularName
import lang.temper.name.ModuleName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.PropertyShape
import lang.temper.type.TypeDefinition
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Type2
import lang.temper.type2.TypeCategory
import lang.temper.type2.sigForFunInterfaceType
import lang.temper.type2.typeCategory
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TString

class CppTranslator(
    cppNames: CppNames,
    private val cppLibraryName: String? = null,
    @Suppress("unused")
    private val dependenciesBuilder: Dependencies.Builder<CppBackend>? = null,
) {
    val cpp = CppBuilder(cppNames)
    val includes = mutableSetOf<String>()
    var currentModuleLocation: ModuleName? = null

    // Maps internal property names to their external dotName text.
    // Populated during TypeDeclaration processing so property accesses use consistent names.
    // Uses CppName text as key (derived from ResolvedName) for reliable matching
    // since SourceName uses identity comparison.
    private val propertyDotNames = mutableMapOf<String, String>()

    /** Stable string key for a ResolvedName, based on the C++ name it would generate. */
    private fun propKey(name: ResolvedName): String = cpp.name(name).id.text

    // Maps (typeName, propertyDotName) -> getter method C++ name for abstract properties.
    // Used by GetAbstractProperty to generate getter method calls instead of field access.
    private val getterMethodNames = mutableMapOf<String, Cpp.SingleName>()

    // Maps propertyDotName -> setter method C++ name.
    // Used by SetAbstractProperty/SetBackedProperty to generate setter method calls.
    private val setterMethodNames = mutableMapOf<String, Cpp.SingleName>()

    // Test info collected during translation: (C++ function name, raw test display name)
    val testInfos = mutableListOf<Pair<String, String>>()

    // Maps TypeFormal definitions to their C++ template parameter names.
    // Populated before translating generic function/type bodies so that type
    // references within the body use the same names as the template declaration.
    // Keyed by TypeDefinition identity AND by name text for fallback matching,
    // since TmpL may use different TypeFormal instances for the same logical
    // type parameter (e.g., in localized imports).
    private val typeFormalNames = java.util.IdentityHashMap<lang.temper.type.TypeDefinition, Cpp.SingleName>()
    private val typeFormalNamesByText = mutableMapOf<String, Cpp.SingleName>()

    /** Generate a key for matching type formals by base name text.
     *  Different SourceName instances for the same type parameter have the same
     *  baseName.nameText but different uids, so we match on the base name only. */
    private fun typeFormalKey(def: lang.temper.type.TypeDefinition): String {
        val name = def.name
        return when (name) {
            is SourceName -> "tf:${name.baseName.nameText}"
            is Temporary -> "tf:${name.nameHint}"
            is ExportedName -> "tf:${name.baseName.nameText}"
            is BuiltinName -> "tf:${name.builtinKey}"
        }
    }

    // Maps C++ name text of imported names to their source module and external name.
    // Keyed on CppName text since SourceName uses identity comparison.
    private data class ImportInfo(val sourceModule: ModuleName, val externalName: ResolvedName)
    private val importedNames = mutableMapOf<String, ImportInfo>()

    /** Populate importedNames from module imports. */
    private fun preprocessImports(mod: TmpL.Module) {
        importedNames.clear()
        for (import in mod.imports) {
            val localName = import.localName?.name ?: continue
            val sourceModule = import.path?.to ?: continue
            val externalName = import.externalName.name
            val key = cpp.name(localName).id.text
            importedNames[key] = ImportInfo(sourceModule, externalName)
        }
    }

    /** Look up the source module for a name, checking imports first. */
    private fun sourceModuleFor(name: ResolvedName): ImportInfo? {
        val key = cpp.name(name).id.text
        importedNames[key]?.let { return it }
        if (name is ModularName) {
            val loc = name.origin.loc
            if (loc is ModuleName && loc != currentModuleLocation) {
                return ImportInfo(loc, name)
            }
        }
        return null
    }

    /**
     * Resolves a TmpL.Id to its C++ name, qualifying with namespace
     * if it comes from a different module.
     */
    private fun resolveNameCrossModule(id: TmpL.Id): Cpp.Name {
        val info = sourceModuleFor(id.name) ?: return cpp.name(id)
        includes.add(cpp.includePathForModule(info.sourceModule))
        // Use the external name from the source module, not the localized name
        val externalCppName = cpp.name(info.externalName)
        return cpp.scopedName(cpp.nameForModule(info.sourceModule), externalCppName)
    }

    /**
     * Resolves a ResolvedName to its C++ name, qualifying with namespace
     * if it comes from a different module.
     */
    private fun resolveNameCrossModule(name: ResolvedName): Cpp.Name {
        val info = sourceModuleFor(name) ?: return cpp.name(name)
        includes.add(cpp.includePathForModule(info.sourceModule))
        val externalCppName = cpp.name(info.externalName)
        return cpp.scopedName(cpp.nameForModule(info.sourceModule), externalCppName)
    }

    /**
     * Resolves a TypeDefinition to a qualified C++ name, adding namespace
     * qualification and includes for cross-module types.
     * Used by SupportNetwork for runtime type operations.
     */
    fun resolveTypeName(def: lang.temper.type.TypeDefinition): Cpp.Name {
        // Check if this is a type formal with a known template parameter name
        (typeFormalNames[def] ?: typeFormalNamesByText[typeFormalKey(def)])?.let { return it }
        val loc = def.sourceLocation
        return when (loc) {
            ImplicitsCodeLocation -> {
                val defName = def.name
                when (defName) {
                    is ExportedName -> cpp.name(TEMPER_CORE_NAMESPACE, defName.baseName.builtinKey)
                    is SourceName -> cpp.name(TEMPER_CORE_NAMESPACE, defName.baseName.builtinKey)
                    is Temporary -> cpp.name(def.name)
                    is BuiltinName -> cpp.name(def.name)
                }
            }
            is ModuleName -> {
                val baseName = cpp.name(def.name)
                if (loc == currentModuleLocation) {
                    baseName
                } else {
                    includes.add(cpp.includePathForModule(loc))
                    cpp.name(cpp.nameForModule(loc), baseName)
                }
            }
        }
    }

    /** Whether Object<T> is specialized to a value type (not shared_ptr-wrapped). */
    private fun isValueType(type: Type2): Boolean {
        val def = type.definition
        return def == WellKnownTypes.intTypeDefinition ||
            def == WellKnownTypes.int64TypeDefinition ||
            def == WellKnownTypes.float64TypeDefinition ||
            def == WellKnownTypes.booleanTypeDefinition ||
            def == WellKnownTypes.stringTypeDefinition ||
            def == WellKnownTypes.voidTypeDefinition
    }

    /** Whether a TypeDefinition is a value type (not shared_ptr-wrapped). */
    internal fun isValueTypeDef(def: TypeDefinition?): Boolean =
        def == WellKnownTypes.intTypeDefinition ||
            def == WellKnownTypes.int64TypeDefinition ||
            def == WellKnownTypes.float64TypeDefinition ||
            def == WellKnownTypes.booleanTypeDefinition ||
            def == WellKnownTypes.stringTypeDefinition ||
            def == WellKnownTypes.voidTypeDefinition ||
            def == WellKnownTypes.stringIndexTypeDefinition ||
            def == WellKnownTypes.noStringIndexTypeDefinition ||
            def == WellKnownTypes.stringIndexOptionTypeDefinition

    /** Whether a TmpL type represents AnyValue (needs boxing for value types). */
    private fun isAnyValueTmpLType(type: TmpL.Type?): Boolean = when (type) {
        is TmpL.TopType -> true
        is TmpL.NominalType -> type.typeName.sourceDefinition == WellKnownTypes.anyValueTypeDefinition
        else -> false
    }

    /**
     * Detect compile-time type mismatches that would fail in C++.
     * Returns true if the expression type is incompatible with the declared type for value types.
     */
    private fun isTypeMismatch(declaredType: TmpL.Type, expr: TmpL.Expression): Boolean {
        val exprType = expr.type
        val declDef = when (declaredType) {
            is TmpL.NominalType -> declaredType.typeName.sourceDefinition
            else -> return false
        }
        val exprDef = exprType.definition
        // Only check value type mismatches (reference types use shared_ptr which handles polymorphism)
        val valueTypeDefs = setOf(
            WellKnownTypes.intTypeDefinition,
            WellKnownTypes.int64TypeDefinition,
            WellKnownTypes.float64TypeDefinition,
            WellKnownTypes.booleanTypeDefinition,
            WellKnownTypes.stringTypeDefinition,
        )
        if (declDef in valueTypeDefs && exprDef in valueTypeDefs && declDef != exprDef) {
            return true
        }
        return false
    }

    /**
     * Detect compile-time type mismatches using Type2 (for Assignments).
     */
    private fun isTypeMismatch2(declaredType: Type2, expr: TmpL.Expression): Boolean {
        val exprDef = expr.type.definition
        val declDef = declaredType.definition
        val valueTypeDefs = setOf(
            WellKnownTypes.intTypeDefinition,
            WellKnownTypes.int64TypeDefinition,
            WellKnownTypes.float64TypeDefinition,
            WellKnownTypes.booleanTypeDefinition,
            WellKnownTypes.stringTypeDefinition,
        )
        if (declDef in valueTypeDefs && exprDef in valueTypeDefs && declDef != exprDef) {
            return true
        }
        return false
    }

    /** Translate a Type2 to a C++ type. Used by SupportNetwork inline code. */
    fun translateType2(type: Type2): Cpp.Type {
        val def = type.definition
        // Handle functional interface types (Fn__NNN etc.)
        if (
            def != WellKnownTypes.functionTypeDefinition &&
            def.typeCategory == TypeCategory.Functional &&
            type is lang.temper.type2.DefinedType
        ) {
            val sig = sigForFunInterfaceType(type)
            if (sig != null) {
                val retType = translateType2(sig.returnType2)
                val paramTypes = sig.allValueFormals.map {
                    translateType2(it.type)
                }
                return cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Function"),
                    listOf(retType) + paramTypes,
                )
            }
        }
        val bindings = type.bindings
        val typeArgs = bindings.map { translateType2(it) }
        val innerType = when {
            def == WellKnownTypes.intTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Int")
            def == WellKnownTypes.int64TypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Int64")
            def == WellKnownTypes.float64TypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Float64")
            def == WellKnownTypes.booleanTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Boolean")
            def == WellKnownTypes.stringTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "String")
            def == WellKnownTypes.voidTypeDefinition -> return cpp.name(TEMPER_CORE_NAMESPACE, "Void")
            def == WellKnownTypes.neverTypeDefinition -> return cpp.name(TEMPER_CORE_NAMESPACE, "Never")
            def == WellKnownTypes.functionTypeDefinition && typeArgs.isNotEmpty() -> {
                // Bindings order: [Param1, ..., ParamN, Return]
                // C++ Function<Ret, Params...> expects return first
                val reordered = listOf(typeArgs.last()) +
                    typeArgs.dropLast(1)
                return cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Function"),
                    reordered,
                )
            }
            def == WellKnownTypes.functionTypeDefinition ->
                return cpp.name(TEMPER_CORE_NAMESPACE, "Function")
            def == WellKnownTypes.listTypeDefinition && typeArgs.isNotEmpty() ->
                return cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "List"), typeArgs),
                )
            def == WellKnownTypes.listBuilderTypeDefinition && typeArgs.isNotEmpty() ->
                return cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "ListBuilder"), typeArgs),
                )
            def == WellKnownTypes.mapTypeDefinition && typeArgs.isNotEmpty() ->
                return cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Map"), typeArgs),
                )
            def == WellKnownTypes.mapBuilderTypeDefinition && typeArgs.isNotEmpty() ->
                return cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "MapBuilder"), typeArgs),
                )
            def == WellKnownTypes.dequeTypeDefinition && typeArgs.isNotEmpty() ->
                return cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Deque"), typeArgs),
                )
            else -> {
                // Check if this is a type formal with a known template parameter name
                (typeFormalNames[def] ?: typeFormalNamesByText[typeFormalKey(def)])?.let { return it }
                // For user-defined types, wrap in Object<TypeName>
                // Qualify with namespace if from a different module.
                val typeName = when (val loc = def.sourceLocation) {
                    ImplicitsCodeLocation -> {
                        val defName = def.name
                        val key = when (defName) {
                            is ExportedName -> defName.baseName.builtinKey
                            is SourceName -> defName.baseName.builtinKey
                            is Temporary -> null
                            is BuiltinName -> null
                        }
                        if (key != null) {
                            translateImplicitsType(key)
                        } else {
                            cpp.name(def.name)
                        }
                    }
                    is ModuleName -> {
                        val baseName = cpp.name(def.name)
                        if (loc == currentModuleLocation) {
                            baseName
                        } else {
                            includes.add(cpp.includePathForModule(loc))
                            cpp.name(cpp.nameForModule(loc), baseName)
                        }
                    }
                }
                val base = if (typeArgs.isNotEmpty()) {
                    cpp.template(typeName, typeArgs)
                } else {
                    typeName
                }
                return cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Object"), base)
            }
        }
        return cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Object"), listOf(innerType))
    }

    private fun translateImplicitsType(builtinKey: String): Cpp.Type = when (builtinKey) {
        "AnyValue" -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValue")
        "Fn" -> cpp.name(TEMPER_CORE_NAMESPACE, "Function")
        else -> cpp.name(TEMPER_CORE_NAMESPACE, builtinKey)
    }

    /** Translate a NominalType as a base class name (no Object<> wrapper). */
    private fun translateSuperType(type: TmpL.NominalType): Cpp.Type {
        val base = translateTypeName(type.typeName)
        return if (type.params.isEmpty()) {
            base
        } else {
            cpp.template(base, type.params.map { param -> translateType(param) })
        }
    }

    private fun translateTypeName(name: TmpL.TypeName): Cpp.Type = when (name) {
        is TmpL.ConnectedToTypeName -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValue")
        is TmpL.TemperTypeName -> when (val def = name.typeDefinition) {
            WellKnownTypes.anyValueTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValue")
            WellKnownTypes.booleanTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Boolean")
            WellKnownTypes.float64TypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Float64")
            WellKnownTypes.functionTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Function")
            WellKnownTypes.intTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Int")
            WellKnownTypes.nullTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Null")
            WellKnownTypes.promiseTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Promise")
            WellKnownTypes.promiseBuilderTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "PromiseBuilder")
            WellKnownTypes.stringTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "String")
            WellKnownTypes.symbolTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Symbol")
            WellKnownTypes.typeTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Type")
            WellKnownTypes.voidTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Void")
            else -> {
                // Check if this is a type formal with a known template parameter name
                val typeFormalName = typeFormalNames[def] ?: typeFormalNamesByText[typeFormalKey(def)]
                if (typeFormalName != null) {
                    typeFormalName
                } else {
                    when (val loc = def.sourceLocation) {
                        ImplicitsCodeLocation -> when (val defName = def.name) {
                            is ExportedName -> translateImplicitsType(defName.baseName.builtinKey)
                            is SourceName -> translateImplicitsType(defName.baseName.builtinKey)
                            is Temporary -> TODO()
                            is BuiltinName -> TODO()
                        }
                        is ModuleName -> {
                            val rest = cpp.name(def.name)
                            if (loc == currentModuleLocation) {
                                rest
                            } else {
                                // Track cross-module include dependency
                                includes.add(cpp.includePathForModule(loc))
                                cpp.name(cpp.nameForModule(loc), rest)
                            }
                        }
                    }
                }
            }
        }
    }

    private val inTranslateType = mutableListOf<TmpL.Type>()
    private fun translateType(type: TmpL.AType) = translateType(type.ot)

    private fun translateType(type: TmpL.Type): Cpp.Type = cpp.pos(type) {
        if (inTranslateType.contains(type)) {
            TODO("recursive type: $type")
        }
        inTranslateType.add(type)
        val ret = when (type) {
            is TmpL.FunctionType -> {
                val ret = translateType(type.returnType)
                val params = type.valueFormals.formals.map { formal ->
                    translateType(formal.type)
                }
                cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Function"),
                    buildList {
                        add(ret)
                        addAll(params)
                    },
                )
            }
            is TmpL.TypeIntersection -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValue")
            is TmpL.TypeUnion -> {
                var isBubble = false
                var isNull = false
                val other = mutableListOf<TmpL.Type>()

                fun addType(type: TmpL.Type) {
                    when (type) {
                        is TmpL.FunctionType -> other.add(type)
                        is TmpL.TypeIntersection -> other.add(type)
                        is TmpL.TypeUnion -> {
                            type.types.forEach(::addType)
                        }
                        is TmpL.NominalType -> when (type.typeName.sourceDefinition) {
                            WellKnownTypes.nullTypeDefinition -> {
                                isNull = true
                            }
                            else -> other.add(type)
                        }
                        is TmpL.BubbleType -> {
                            isBubble = true
                        }
                        else -> other.add(type)
                    }
                }

                type.types.forEach(::addType)

                val base = if (other.size == 1) {
                    val first = translateType(other[0])
                    if (isNull) {
                        cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Nullable"), first)
                    } else {
                        first
                    }
                } else if (isNull) {
                    cpp.name(TEMPER_CORE_NAMESPACE, "Null")
                } else {
                    return@pos cpp.name(TEMPER_CORE_NAMESPACE, "Void")
                }

                if (isBubble) {
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Bubble"), base)
                } else {
                    base
                }
            }
            is TmpL.GarbageType -> TODO()
            is TmpL.NominalType -> {
                // Check if this is a type formal (template parameter)
                // If so, return without Object<> wrapping to enable
                // C++ template argument deduction
                val typeFormalMatch = when (val tn = type.typeName) {
                    is TmpL.TemperTypeName -> {
                        val def = tn.typeDefinition
                        typeFormalNames[def]
                            ?: typeFormalNamesByText[typeFormalKey(def)]
                    }
                    else -> null
                }
                if (typeFormalMatch != null) {
                    typeFormalMatch
                } else {
                    cpp.template(
                        cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                        if (type.params.isEmpty()) {
                            translateTypeName(type.typeName)
                        } else {
                            cpp.template(
                                translateTypeName(type.typeName),
                                type.params.map { param -> translateType(param) },
                            )
                        },
                    )
                }
            }
            is TmpL.BubbleType -> TODO()
            is TmpL.NeverType -> cpp.type("void")
            is TmpL.TopType -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase")
        }
        inTranslateType.removeLast()
        return@pos ret
    }

    private fun todoCommentOf(value: TmpL.ExpressionOrCallable): Cpp.Expr = cpp.callExpr(
        cpp.name("exit"),
        cpp.literal(0),
    ).withComment("${value.javaClass}")

    private fun todoCommentOf(value: TmpL.Statement, stmts: Iterable<Cpp.Stmt>): List<Cpp.Stmt> = buildList {
        cpp.comment("${value.javaClass}")
        addAll(stmts)
    }

    private fun todoCommentOf(
        value: TmpL.Statement,
        vararg stmts: Cpp.Stmt,
    ): List<Cpp.Stmt> = todoCommentOf(value, stmts.toList())

    private fun translateCallable(fn: TmpL.Callable): Cpp.Expr = cpp.pos(fn) {
        when (fn) {
            is TmpL.InlineSupportCodeWrapper -> todoCommentOf(fn)
            is TmpL.FnReference -> resolveNameCrossModule(fn.id)
            is TmpL.FunInterfaceCallable -> translateExpression(fn.expr)
            is TmpL.ConstructorReference -> cpp.scopedName(
                translateTypeName(fn.typeName),
                cpp.singleName(CppName("make")),
            )

            is TmpL.GarbageCallable -> todoCommentOf(fn)
            is TmpL.MethodReference ->
                when (val subject = fn.subject) {
                    is TmpL.Expression -> {
                        val methodName = cpp.singleName(CppName(fn.methodName.dotNameText))
                        if (isValueType(subject.type)) {
                            cpp.memberExpr(translateExpression(subject), methodName)
                        } else {
                            cpp.op("->", translateExpression(subject), methodName)
                        }
                    }

                    is TmpL.ConnectedToTypeName -> cpp.scopedName(
                        translateTypeName(subject),
                        cpp.singleName(CppName(fn.methodName.dotNameText)),
                    )
                    is TmpL.TemperTypeName -> cpp.scopedName(
                        translateTypeName(subject),
                        cpp.singleName(CppName(fn.methodName.dotNameText)),
                    )
                }
        }
    }

    private fun translateExpression(expr: TmpL.Expression): Cpp.Expr = cpp.pos(expr) {
        when (expr) {
            is TmpL.AwaitExpression -> TODO()
            is TmpL.BubbleSentinel -> {
                // Compile-time type errors generate bubble at runtime
                cpp.callExpr(
                    cpp.template(
                        cpp.name(TEMPER_CORE_NAMESPACE, "bubble"),
                        listOf(cpp.name(TEMPER_CORE_NAMESPACE, "Void")),
                    ),
                    emptyList(),
                )
            }
            is TmpL.CallExpression -> {
                when (val fn = expr.fn) {
                    is TmpL.GarbageCallable -> {
                        todoCommentOf(fn)
                    }

                    is TmpL.InlineSupportCodeWrapper -> {
                        when (val supportCode = fn.supportCode) {
                            is CppInlineSupportCode -> supportCode.inlineToTree(
                                expr.pos,
                                expr.mapParameters { actual, staticType, _ ->
                                    TypedArg(
                                        translateExpression(actual as TmpL.Expression),
                                        staticType ?: WellKnownTypes.anyValueType2,
                                    )
                                },
                                expr.type,
                                this,
                            ) as Cpp.Expr

                            else -> todoCommentOf(expr)
                        }
                    }

                    is TmpL.ConstructorReference -> {
                        // For constructor calls, get the concrete type from the
                        // call expression's return type (not the generic signature)
                        val callReturnType = expr.type
                        val baseTypeName = translateTypeName(fn.typeName)
                        val bindings = callReturnType.bindings
                        val qualifiedType = if (bindings.isNotEmpty()) {
                            cpp.template(baseTypeName, bindings.map { translateType2(it) })
                        } else {
                            baseTypeName
                        }
                        val callable = cpp.scopedName(
                            qualifiedType,
                            cpp.singleName(CppName("make")),
                        )
                        val sig = fn.type
                        val numRequired = sig.requiredInputTypes.size -
                            (if (sig.hasThisFormal) 1 else 0)
                        val optionalTypes = sig.optionalInputTypes
                        cpp.callExpr(
                            callable,
                            expr.parameters.mapIndexed { idx, actual ->
                                val actualExpr = actual as TmpL.Expression
                                val optionalIdx = idx - numRequired
                                val isOptionalParam = optionalIdx >= 0
                                val isNullLiteral = actualExpr is TmpL.ValueReference &&
                                    actualExpr.value.typeTag == TNull
                                if (isOptionalParam && optionalIdx < optionalTypes.size) {
                                    if (isNullLiteral) {
                                        cpp.callExpr(
                                            cpp.template(
                                                cpp.name(
                                                    TEMPER_CORE_NAMESPACE,
                                                    "NullableParam",
                                                ),
                                                listOf(
                                                    translateType2(
                                                        optionalTypes[optionalIdx],
                                                    ),
                                                ),
                                            ),
                                        )
                                    } else {
                                        cpp.callExpr(
                                            cpp.template(
                                                cpp.name(
                                                    TEMPER_CORE_NAMESPACE,
                                                    "NullableParam",
                                                ),
                                                listOf(
                                                    translateType2(
                                                        optionalTypes[optionalIdx],
                                                    ),
                                                ),
                                            ),
                                            translateExpression(actualExpr),
                                        )
                                    }
                                } else {
                                    translateExpression(actualExpr)
                                }
                            },
                        )
                    }

                    else -> {
                        val sig = fn.type
                        // Check for argument type mismatches that would fail in C++
                        val hasArgMismatch = run {
                            val paramTypes = sig.requiredInputTypes.drop(
                                if (sig.hasThisFormal) 1 else 0,
                            )
                            expr.parameters.filterIsInstance<TmpL.Expression>().withIndex().any { (idx, arg) ->
                                val paramType = paramTypes.getOrNull(idx) ?: return@any false
                                isTypeMismatch2(paramType, arg)
                            }
                        }
                        if (hasArgMismatch) {
                            return@pos cpp.callExpr(
                                cpp.template(
                                    cpp.name(TEMPER_CORE_NAMESPACE, "bubble"),
                                    listOf(cpp.name(TEMPER_CORE_NAMESPACE, "Void")),
                                ),
                                emptyList(),
                            )
                        }
                        // Use contextualized sig for optional types
                        // so type formals are resolved to concrete types
                        val ctxSig = expr.contextualizedSig
                        // requiredInputTypes includes `this` when hasThisFormal,
                        // but expr.parameters doesn't include `this`
                        val numRequired = sig.requiredInputTypes.size -
                            (if (sig.hasThisFormal) 1 else 0)
                        val optionalTypes = ctxSig.optionalInputTypes
                        // Add explicit template args for template functions
                        // to help C++ template argument deduction
                        val callableExpr = run {
                            val base = translateCallable(fn)
                            val typeBindings = expr.typeActuals.bindings
                            if (fn is TmpL.FnReference &&
                                typeBindings.isNotEmpty() &&
                                fn.type.typeFormals.isNotEmpty() &&
                                base is Cpp.Type
                            ) {
                                val typeArgs = fn.type.typeFormals.mapNotNull { formal ->
                                    typeBindings[formal]?.let { translateType2(it) }
                                }
                                if (typeArgs.isNotEmpty()) {
                                    cpp.template(base, typeArgs) as Cpp.Expr
                                } else {
                                    base
                                }
                            } else {
                                base
                            }
                        }
                        val restType = sig.restInputsType
                        val numNonRest = numRequired +
                            optionalTypes.size
                        val translatedArgs = mutableListOf<Cpp.Expr>()
                        for (
                        (idx, actual) in
                        expr.parameters.withIndex()
                        ) {
                            if (actual is TmpL.RestSpread) {
                                translatedArgs.add(
                                    cpp.name(actual.parameterName),
                                )
                                continue
                            }
                            val actualExpr = actual as TmpL.Expression
                            if (restType != null && idx >= numNonRest) {
                                // Rest args handled below
                                continue
                            }
                            val optionalIdx = idx - numRequired
                            val isOptionalParam = optionalIdx >= 0
                            val isNullLiteral =
                                actualExpr is TmpL.ValueReference &&
                                    actualExpr.value.typeTag == TNull
                            if (
                                isOptionalParam &&
                                optionalIdx < optionalTypes.size
                            ) {
                                if (isNullLiteral) {
                                    translatedArgs.add(
                                        cpp.callExpr(
                                            cpp.template(
                                                cpp.name(
                                                    TEMPER_CORE_NAMESPACE,
                                                    "NullableParam",
                                                ),
                                                listOf(
                                                    translateType2(
                                                        optionalTypes[
                                                            optionalIdx,
                                                        ],
                                                    ),
                                                ),
                                            ),
                                        ),
                                    )
                                } else {
                                    translatedArgs.add(
                                        cpp.callExpr(
                                            cpp.template(
                                                cpp.name(
                                                    TEMPER_CORE_NAMESPACE,
                                                    "NullableParam",
                                                ),
                                                listOf(
                                                    translateType2(
                                                        optionalTypes[
                                                            optionalIdx,
                                                        ],
                                                    ),
                                                ),
                                            ),
                                            translateExpression(
                                                actualExpr,
                                            ),
                                        ),
                                    )
                                }
                            } else {
                                translatedArgs.add(
                                    translateExpression(actualExpr),
                                )
                            }
                        }
                        // Wrap rest args into a list
                        if (restType != null) {
                            val restArgs = expr.parameters
                                .drop(numNonRest)
                                .filterIsInstance<TmpL.Expression>()
                            val elemType = translateType2(restType)
                            val listExpr = cpp.callExpr(
                                cpp.template(
                                    cpp.name(
                                        TEMPER_CORE_NAMESPACE,
                                        "list_of",
                                    ),
                                    listOf(elemType),
                                ),
                                restArgs.map {
                                    translateExpression(it)
                                },
                            )
                            translatedArgs.add(listExpr)
                        }
                        cpp.callExpr(callableExpr, translatedArgs)
                    }
                }
            }
            is TmpL.CastExpression -> {
                // Use checked_cast for shared_ptr types (throws on failure), static_cast for value types
                val targetType = translateType(expr.checkedType)
                val sourceExpr = translateExpression(expr.expr)
                val innerCheckedType = expr.checkedType.privOtOrNull
                val isCastToValueType = innerCheckedType != null &&
                    innerCheckedType is TmpL.NominalType &&
                    isValueTypeDef(innerCheckedType.typeName.sourceDefinition)
                if (isCastToValueType) {
                    // Casting to a value type — identity cast (value types can't be cast dynamically)
                    sourceExpr
                } else if (targetType is Cpp.TemplateType) {
                    val innerType = targetType.args.firstOrNull() ?: targetType
                    cpp.callExpr(
                        cpp.template(
                            cpp.name(TEMPER_CORE_NAMESPACE, "checked_cast"),
                            listOf(innerType),
                        ),
                        listOf(sourceExpr),
                    )
                } else {
                    cpp.cast(targetType, sourceExpr)
                }
            }
            is TmpL.FunInterfaceExpression -> translateCallable(expr.callable)
            is TmpL.GarbageExpression -> todoCommentOf(expr)
            is TmpL.GetAbstractProperty -> {
                // Abstract properties are accessed via getter methods
                val propDotName = when (val prop = expr.property) {
                    is TmpL.ExternalPropertyId -> prop.name.dotNameText
                    is TmpL.InternalPropertyId -> propertyDotNames[propKey(prop.name.name)] ?: prop.name.name.toString()
                }
                val getterName = getterMethodNames[propDotName]
                if (getterName != null) {
                    // Call the getter method: subject->getter()
                    cpp.callExpr(
                        cpp.op("->", translateExpression(expr.subject), getterName),
                        listOf(),
                    )
                } else {
                    // Fallback to field access
                    cpp.op("->", translateExpression(expr.subject), translatePropertyId(expr.property))
                }
            }
            is TmpL.GetBackedProperty -> {
                val propName = translatePropertyId(expr.property)
                when (val subject = expr.subject) {
                    is TmpL.Expression -> cpp.op("->", translateExpression(subject), propName)
                    is TmpL.ConnectedToTypeName -> cpp.scopedName(translateTypeName(subject), propName)
                    is TmpL.TemperTypeName -> cpp.scopedName(translateTypeName(subject), propName)
                }
            }
            is TmpL.InstanceOfExpression -> {
                val targetType = translateType(expr.checkedType)
                val sourceExpr = translateExpression(expr.expr)
                // Check if the checked type is a value type (Object<T> resolves to T, not shared_ptr<T>)
                val innerCheckedType = expr.checkedType.privOtOrNull
                val checkedTypeDef = when (innerCheckedType) {
                    is TmpL.NominalType -> innerCheckedType.typeName.sourceDefinition
                    else -> null
                }
                val isCheckedValueType = isValueTypeDef(checkedTypeDef)
                if (isCheckedValueType) {
                    // Value type — can't use dynamic_pointer_cast.
                    // Check if source is nullable (shared_ptr or function type that might be null)
                    val sourceType = expr.expr.type
                    val isSourceRefType = !isValueType(sourceType) &&
                        sourceType.definition != WellKnownTypes.nullTypeDefinition
                    if (isSourceRefType) {
                        // Source is a reference type (shared_ptr) being checked against a value type
                        // This is effectively a null check
                        cpp.op("!=", listOf(sourceExpr, cpp.literal(cpp.raw("nullptr"))))
                    } else {
                        // Source is already a value type — always true
                        cpp.literal(true)
                    }
                } else if (targetType is Cpp.TemplateType) {
                    // Object<T> = shared_ptr<T> → use dynamic_pointer_cast
                    val innerType = targetType.args.firstOrNull() ?: targetType
                    cpp.op(
                        "!=",
                        listOf(
                            cpp.callExpr(
                                cpp.template(
                                    cpp.name("std", "dynamic_pointer_cast"),
                                    listOf(innerType),
                                ),
                                listOf(sourceExpr),
                            ),
                            cpp.literal(cpp.raw("nullptr")),
                        ),
                    )
                } else {
                    // Value type — check is always true at this point
                    cpp.literal(true)
                }
            }
            is TmpL.InfixOperation -> {
                val left = translateExpression(expr.left)
                val right = translateExpression(expr.right)
                cpp.op(expr.op.kind.outputToken.text, left, right)
            }
            is TmpL.PrefixOperation -> {
                cpp.unaryExpr(cpp.unaryOp(expr.op.kind.outputToken.text), translateExpression(expr.operand))
            }
            is TmpL.Reference -> resolveNameCrossModule(expr.id)
            is TmpL.RestParameterCountExpression -> cpp.callExpr(
                cpp.name(TEMPER_CORE_NAMESPACE, "length"),
                listOf(cpp.name(expr.parameterName)),
            )
            is TmpL.RestParameterExpression -> cpp.callExpr(
                cpp.name(TEMPER_CORE_NAMESPACE, "get"),
                listOf(
                    cpp.name(expr.parameterName),
                    cpp.literal(cpp.raw(expr.index.index.toString())),
                ),
            )
            is TmpL.This -> cpp.name(expr.id)
            is TmpL.UncheckedNotNullExpression -> cpp.callExpr(
                cpp.name(TEMPER_CORE_NAMESPACE, "not_null"),
                translateExpression(expr.expression),
            )
            is TmpL.ValueReference -> {
                val value = expr.value
                when (value.typeTag) {
                    TBoolean -> cpp.literal(TBoolean.unpack(value))
                    TInt -> cpp.literal(TInt.unpack(value))
                    TInt64 -> cpp.literal(cpp.raw("${TInt64.unpack(value)}LL"))
                    TFloat64 -> {
                        val d = TFloat64.unpack(value)
                        when {
                            d.isNaN() -> cpp.literal(cpp.raw("std::numeric_limits<double>::quiet_NaN()"))
                            d.isInfinite() && d > 0 ->
                                cpp.literal(cpp.raw("std::numeric_limits<double>::infinity()"))
                            d.isInfinite() && d < 0 ->
                                cpp.literal(cpp.raw("(-std::numeric_limits<double>::infinity())"))
                            else -> cpp.literal(d)
                        }
                    }
                    TString -> cpp.literal(TString.unpack(value))
                    TNull -> {
                        // Use 0 which converts to nullptr for
                        // shared_ptr. For value types, 0 converts to
                        // the default (0/false). For NullableParam,
                        // special handling at call sites wraps this.
                        val def = expr.type.definition
                        when (def) {
                            WellKnownTypes.stringTypeDefinition ->
                                cpp.literal(cpp.raw("\"\""))
                            else ->
                                cpp.literal(cpp.raw("0"))
                        }
                    }
                    TProblem -> cpp.literal(cpp.raw("/* error value */ 0"))
                    else -> {
                        val type = expr.type
                        when (type.definition) {
                            WellKnownTypes.voidType.definition -> cpp.literal(cpp.raw("TEMPER_VOID"))
                            WellKnownTypes.typeType.definition -> cpp.literal(
                                cpp.raw("TEMPER_TYPE(${type.definition.name.displayName})"),
                            )
                            else -> cpp.literal(cpp.raw("/* TODO: literal ${value.typeTag} */ 0"))
                        }
                    }
                }
            }
        }
    }

    private fun translateExpressionOrNull(expr: TmpL.Expression?): Cpp.Expr? = when (expr) {
        null -> null
        else -> translateExpression(expr)
    }

    private fun translateStatement(stmt: TmpL.Statement): Iterable<Cpp.Stmt> = cpp.pos(stmt) {
        when (stmt) {
            is TmpL.Assignment -> {
                // Skip assignments to imported names (they alias the external)
                val leftKey = cpp.name(stmt.left).id.text
                if (leftKey in importedNames) {
                    emptyList()
                } else {
                    val right = stmt.right
                    val isAnyValueTarget = stmt.type.definition ==
                        WellKnownTypes.anyValueTypeDefinition
                    val isRhsNever = right is TmpL.Expression &&
                        right.type.definition == WellKnownTypes.neverTypeDefinition
                    val rightExpr = if (right is TmpL.Expression && isTypeMismatch2(stmt.type, right)) {
                        // Type mismatch at compile time — generate bubble instead
                        val cppType = translateType2(stmt.type)
                        cpp.callExpr(
                            cpp.template(
                                cpp.name(TEMPER_CORE_NAMESPACE, "bubble"),
                                listOf(cppType),
                            ),
                            emptyList(),
                        )
                    } else if (isRhsNever && isValueTypeDef(stmt.type.definition)) {
                        // Never-typed expression (bubble) assigned to value type
                        // Use target type for bubble template to avoid ambiguous conversion
                        val cppType = translateType2(stmt.type)
                        cpp.callExpr(
                            cpp.template(
                                cpp.name(TEMPER_CORE_NAMESPACE, "bubble"),
                                listOf(cppType),
                            ),
                            emptyList(),
                        )
                    } else if (isAnyValueTarget && right is TmpL.Expression && isValueType(right.type)) {
                        // Boxing value type into AnyValue
                        cpp.callExpr(
                            cpp.name(TEMPER_CORE_NAMESPACE, "any_box"),
                            listOf(translateExpression(right)),
                        )
                    } else {
                        when (right) {
                            is TmpL.Expression -> translateExpression(right)
                            is TmpL.HandlerScope -> TODO()
                        }
                    }
                    listOf(
                        cpp.exprStmt(
                            cpp.op(
                                "=",
                                cpp.name(stmt.left),
                                rightExpr,
                            ),
                        ),
                    )
                }
            }
            is TmpL.BoilerplateCodeFoldEnd -> todoCommentOf(stmt)
            is TmpL.BoilerplateCodeFoldStart -> todoCommentOf(stmt)
            is TmpL.BreakStatement -> when (val label = stmt.label) {
                null -> listOf(cpp.exprStmt(cpp.literal(cpp.raw("break"))))
                else -> {
                    val fmtHints = CppFormattingHints.getInstance()
                    val labelName = toStringViaTokenSink(
                        formattingHints = fmtHints,
                        singleLine = true,
                    ) { cpp.name(label.id).renderTo(it) }
                    listOf(cpp.gotoStmt(cpp.singleName("${labelName}_end")))
                }
            }
            is TmpL.ContinueStatement -> when (val label = stmt.label) {
                null -> listOf(cpp.exprStmt(cpp.literal(cpp.raw("continue"))))
                else -> {
                    val fmtHints = CppFormattingHints.getInstance()
                    val labelName = toStringViaTokenSink(
                        formattingHints = fmtHints,
                        singleLine = true,
                    ) { cpp.name(label.id).renderTo(it) }
                    listOf(cpp.gotoStmt(cpp.singleName(labelName)))
                }
            }
            is TmpL.EmbeddedComment -> todoCommentOf(stmt)
            is TmpL.ExpressionStatement -> listOf(
                cpp.exprStmt(translateExpression(stmt.expression)),
            )

            is TmpL.GarbageStatement -> todoCommentOf(stmt)
            is TmpL.HandlerScope -> {
                todoCommentOf(stmt)
            }
            is TmpL.LocalDeclaration -> {
                val initExpr = stmt.init
                val innerType = stmt.type.privOtOrNull
                val translatedInit = if (
                    initExpr != null &&
                    innerType != null &&
                    isTypeMismatch(innerType, initExpr)
                ) {
                    // Type mismatch at compile time — generate bubble() instead
                    // to avoid C++ static type errors
                    val cppType = translateType(innerType)
                    cpp.callExpr(
                        cpp.template(
                            cpp.name(TEMPER_CORE_NAMESPACE, "bubble"),
                            listOf(cppType),
                        ),
                        emptyList(),
                    )
                } else if (
                    initExpr != null &&
                    isAnyValueTmpLType(innerType) &&
                    isValueType(initExpr.type)
                ) {
                    // Boxing value type into AnyValue
                    cpp.callExpr(
                        cpp.name(TEMPER_CORE_NAMESPACE, "any_box"),
                        listOf(translateExpression(initExpr)),
                    )
                } else {
                    translateExpressionOrNull(initExpr)
                }
                listOf(
                    cpp.varDef(
                        translateType(stmt.type),
                        cpp.name(stmt.name),
                        translatedInit,
                    ),
                )
            }

            is TmpL.LocalFunctionDeclaration -> {
                // Only emit the assignment here; the forward declaration
                // is hoisted by translateBlock to support mutual recursion.
                val fmtHints = CppFormattingHints.getInstance()
                val params = stmt.parameters.parameters.joinToString(", ") { param ->
                    val typeStr = toStringViaTokenSink(
                        formattingHints = fmtHints,
                        singleLine = true,
                    ) { translateType(param.type).renderTo(it) }
                    val nameStr = toStringViaTokenSink(
                        formattingHints = fmtHints,
                        singleLine = true,
                    ) { cpp.name(param.name).renderTo(it) }
                    "$typeStr $nameStr"
                }
                val retTypeStr = toStringViaTokenSink(
                    formattingHints = fmtHints,
                    singleLine = true,
                ) { translateType(stmt.returnType).renderTo(it) }
                val bodyBlock = translateBlock(stmt.body)
                val bodyStr = toStringViaTokenSink(
                    formattingHints = fmtHints,
                ) { bodyBlock.renderTo(it) }
                val nameStr = toStringViaTokenSink(
                    formattingHints = fmtHints,
                    singleLine = true,
                ) { cpp.name(stmt.name).renderTo(it) }
                val lambdaExpr = "[&]($params) -> $retTypeStr $bodyStr"
                listOf(
                    cpp.exprStmt(cpp.literal(cpp.raw("$nameStr = $lambdaExpr"))),
                )
            }
            is TmpL.ModuleInitFailed -> todoCommentOf(stmt)
            is TmpL.BlockStatement -> listOf(
                translateBlock(stmt),
            )

            is TmpL.ComputedJumpStatement -> todoCommentOf(stmt)
            is TmpL.IfStatement -> listOf(
                cpp.ifStmt(
                    translateExpression(stmt.test),
                    cpp.blockStmt(
                        translateStatement(stmt.consequent),
                    ),
                    stmt.alternate?.let {
                        cpp.blockStmt(translateStatement(it))
                    },
                ),
            )

            is TmpL.LabeledStatement -> {
                val body = translateStatement(stmt.statement).toList()
                val fmtHints = CppFormattingHints.getInstance()
                val labelName = cpp.name(stmt.label.id)
                val labelText = toStringViaTokenSink(
                    formattingHints = fmtHints,
                    singleLine = true,
                ) { labelName.renderTo(it) }
                val endLabel = cpp.singleName("${labelText}_end")
                val result = if (body.isEmpty()) {
                    listOf(cpp.labelStmt(labelName, cpp.exprStmt(cpp.literal(cpp.raw("(void)0")))))
                } else {
                    listOf(cpp.labelStmt(labelName, body.first())) + body.drop(1)
                }
                result + listOf(
                    cpp.labelStmt(endLabel, cpp.exprStmt(cpp.literal(cpp.raw("(void)0")))),
                )
            }
            is TmpL.TryStatement -> {
                val fmtHints = CppFormattingHints.getInstance()
                val tryStmts = translateStatement(stmt.tried).toList()
                val recoverStmts = translateStatement(stmt.recover).toList()
                val tryBodyStr = toStringViaTokenSink(
                    formattingHints = fmtHints,
                ) { cpp.blockStmt(tryStmts).renderTo(it) }
                val catchBodyStr = toStringViaTokenSink(
                    formattingHints = fmtHints,
                ) { cpp.blockStmt(recoverStmts).renderTo(it) }
                listOf(
                    cpp.exprStmt(
                        cpp.literal(cpp.raw("try $tryBodyStr catch (...) $catchBodyStr")),
                    ),
                )
            }
            is TmpL.WhileStatement -> listOf(
                cpp.whileStmt(
                    translateExpression(stmt.test),
                    cpp.blockStmt(
                        translateStatement(stmt.body),
                    ),
                ),
            )
            is TmpL.ReturnStatement -> listOf(
                cpp.returnStmt(
                    translateExpressionOrNull(stmt.expression),
                ),
            )

            is TmpL.SetAbstractProperty -> translateSetProperty(stmt.left, stmt.right, useSetterMethod = true)
            is TmpL.SetBackedProperty -> translateSetProperty(stmt.left, stmt.right, useSetterMethod = false)
            is TmpL.ThrowStatement -> listOf(
                // Throw a runtime_error to trigger the catch handler
                cpp.exprStmt(cpp.literal(cpp.raw("throw std::runtime_error(\"bubble\")"))),
            )
            is TmpL.YieldStatement -> todoCommentOf(stmt)
        }
    }

    private fun translatePropertyId(prop: TmpL.PropertyId): Cpp.SingleName = when (prop) {
        is TmpL.ExternalPropertyId -> cpp.singleName(CppName(fixName(prop.name.dotNameText)))
        is TmpL.InternalPropertyId -> {
            val key = propKey(prop.name.name)
            val dotName = propertyDotNames[key]
            if (dotName != null) {
                cpp.singleName(CppName(fixName(dotName)))
            } else {
                // For backing fields (prefixed with _), strip prefix and look up again
                val name = prop.name.name
                val displayName = name.displayName
                val strippedName = if (displayName.startsWith("_")) displayName.removePrefix("_") else null
                val matchingDotName = strippedName?.let { stripped ->
                    propertyDotNames.entries.firstOrNull { (_, v) ->
                        v == stripped || v == "_$stripped"
                    }?.value
                }
                if (matchingDotName != null) {
                    // Backing field — use the property's dot name with _ prefix
                    cpp.singleName(CppName(fixName("_$matchingDotName")))
                } else {
                    cpp.name(prop.name.name)
                }
            }
        }
    }

    private fun translateSetProperty(
        lval: TmpL.PropertyLValue,
        right: TmpL.Expression,
        useSetterMethod: Boolean = true,
    ): List<Cpp.Stmt> {
        val propSingleName = translatePropertyId(lval.property)
        // Check if there's a setter method registered for this property
        val propDotName = when (val prop = lval.property) {
            is TmpL.ExternalPropertyId -> prop.name.dotNameText
            is TmpL.InternalPropertyId -> {
                val key = propKey(prop.name.name)
                propertyDotNames[key] ?: prop.name.name.displayName
            }
        }
        val setterName = if (useSetterMethod) setterMethodNames[propDotName] else null
        if (setterName != null) {
            // Call setter method instead of direct assignment
            val call: Cpp.Expr = when (val subj = lval.subject) {
                is TmpL.Expression -> cpp.callExpr(
                    cpp.op("->", translateExpression(subj), setterName),
                    translateExpression(right),
                )
                is TmpL.ConnectedToTypeName -> cpp.callExpr(
                    cpp.scopedName(translateTypeName(subj), setterName),
                    translateExpression(right),
                )
                is TmpL.TemperTypeName -> cpp.callExpr(
                    cpp.scopedName(translateTypeName(subj), setterName),
                    translateExpression(right),
                )
            }
            return listOf(cpp.exprStmt(call))
        }
        // Fall back to direct assignment
        val lhs: Cpp.Expr = when (val subj = lval.subject) {
            is TmpL.Expression -> cpp.op("->", translateExpression(subj), propSingleName)
            is TmpL.ConnectedToTypeName -> cpp.scopedName(translateTypeName(subj), propSingleName)
            is TmpL.TemperTypeName -> cpp.scopedName(translateTypeName(subj), propSingleName)
        }
        return listOf(
            cpp.exprStmt(cpp.op("=", lhs, translateExpression(right))),
        )
    }

    private fun translateBlock(block: TmpL.BlockStatement): Cpp.BlockStmt = cpp.pos(block) {
        // Hoist local function declarations: emit forward declarations first,
        // then assignments, to support mutual recursion.
        val fmtHints = CppFormattingHints.getInstance()
        val localFuncDecls = mutableListOf<Cpp.Stmt>()
        val stmts = mutableListOf<Cpp.Stmt>()
        for (stmt in block.statements) {
            if (stmt is TmpL.LocalFunctionDeclaration) {
                // Emit forward declaration (type + name only)
                val paramTypeStrs = stmt.parameters.parameters.joinToString(", ") { param ->
                    toStringViaTokenSink(
                        formattingHints = fmtHints,
                        singleLine = true,
                    ) { translateType(param.type).renderTo(it) }
                }
                val retTypeStr = toStringViaTokenSink(
                    formattingHints = fmtHints,
                    singleLine = true,
                ) { translateType(stmt.returnType).renderTo(it) }
                val funcTypeStr = "std::function<$retTypeStr($paramTypeStrs)>"
                val nameStr = toStringViaTokenSink(
                    formattingHints = fmtHints,
                    singleLine = true,
                ) { cpp.name(stmt.name).renderTo(it) }
                localFuncDecls.add(
                    cpp.exprStmt(cpp.literal(cpp.raw("$funcTypeStr $nameStr"))),
                )
            }
            stmts.addAll(translateStatement(stmt))
        }
        cpp.blockStmt(localFuncDecls + stmts)
    }

    private fun translateBlockWithThis(
        thisType: Cpp.Type,
        thisName: Cpp.SingleName,
        block: TmpL.BlockStatement,
    ): Cpp.BlockStmt = cpp.pos(block) {
        val fmtHints = CppFormattingHints.getInstance()
        cpp.blockStmt(
            buildList {
                // Use borrow_this to create a non-owning shared_ptr from `this`,
                // so that `this` can be passed to functions expecting Object<T>.
                add(
                    cpp.varDef(
                        cpp.singleName(CppName("auto", allowKey = true)),
                        thisName,
                        cpp.callExpr(
                            cpp.name(TEMPER_CORE_NAMESPACE, "borrow_this"),
                            cpp.thisExpr(),
                        ),
                    ),
                )
                // Hoist local function forward declarations
                for (stmt in block.statements) {
                    if (stmt is TmpL.LocalFunctionDeclaration) {
                        val paramTypeStrs = stmt.parameters.parameters.joinToString(", ") { param ->
                            toStringViaTokenSink(
                                formattingHints = fmtHints,
                                singleLine = true,
                            ) { translateType(param.type).renderTo(it) }
                        }
                        val retTypeStr = toStringViaTokenSink(
                            formattingHints = fmtHints,
                            singleLine = true,
                        ) { translateType(stmt.returnType).renderTo(it) }
                        val funcTypeStr = "std::function<$retTypeStr($paramTypeStrs)>"
                        val nameStr = toStringViaTokenSink(
                            formattingHints = fmtHints,
                            singleLine = true,
                        ) { cpp.name(stmt.name).renderTo(it) }
                        add(cpp.exprStmt(cpp.literal(cpp.raw("$funcTypeStr $nameStr"))))
                    }
                }
                block.statements.forEach { stmt ->
                    addAll(translateStatement(stmt))
                }
            },
        )
    }

    /**
     * Wraps optional parameter type in NullableParam<T>.
     */
    private fun translateParamType(formal: TmpL.Formal): Cpp.Type {
        val baseType = translateType(formal.type)
        return if (formal.optional) {
            cpp.template(
                cpp.name(TEMPER_CORE_NAMESPACE, "NullableParam"),
                listOf(baseType),
            )
        } else {
            baseType
        }
    }

    /**
     * Generates overloads for functions with optional trailing params.
     * For each count of dropped trailing optional params, generates a
     * forwarding overload that calls the full function with default
     * NullableParam() for the missing params.
     *
     * @return list of (declaration, definition) pairs for overloads
     */
    private fun generateOptionalOverloads(
        funcName: Cpp.Name,
        retType: Cpp.Type,
        formals: List<TmpL.Formal>,
        declMod: Cpp.DefMod? = null,
    ): List<Pair<Cpp.FuncDecl, Cpp.FuncDef>> {
        val result = mutableListOf<Pair<Cpp.FuncDecl, Cpp.FuncDef>>()
        // Find the index where optional params start at the tail
        val lastRequiredIdx = formals.indexOfLast { !it.optional }
        val firstOptionalIdx = lastRequiredIdx + 1
        if (firstOptionalIdx >= formals.size) return result

        // Generate overloads for each number of provided optionals
        // from (n-1 optionals) down to (0 optionals).
        // Skip numProvided = totalOptionals since the original
        // function already handles all-args case.
        val totalOptionals = formals.size - firstOptionalIdx
        for (numProvided in (totalOptionals - 1) downTo 0) {
            val providedFormals = formals.subList(
                0,
                firstOptionalIdx + numProvided,
            )
            val overloadParamTypes = providedFormals.map {
                translateType(it.type)
            }
            val overloadParams = providedFormals.map {
                cpp.funcParam(translateType(it.type), cpp.name(it.name))
            }
            val callArgs = formals.mapIndexed { idx, f ->
                if (idx < firstOptionalIdx + numProvided) {
                    // Wrap optional params in NullableParam
                    if (f.optional) {
                        cpp.callExpr(
                            cpp.template(
                                cpp.name(
                                    TEMPER_CORE_NAMESPACE,
                                    "NullableParam",
                                ),
                                listOf(translateType(f.type)),
                            ),
                            cpp.name(f.name),
                        )
                    } else {
                        cpp.name(f.name)
                    }
                } else {
                    // Default-constructed NullableParam (no value)
                    cpp.callExpr(
                        cpp.template(
                            cpp.name(
                                TEMPER_CORE_NAMESPACE,
                                "NullableParam",
                            ),
                            listOf(translateType(f.type)),
                        ),
                    )
                }
            }
            val singleName = when (funcName) {
                is Cpp.SingleName -> funcName
                is Cpp.ScopedName -> funcName.member
            }
            val decl = cpp.funcDecl(
                declMod,
                retType.deepCopy(),
                singleName.deepCopy(),
                overloadParamTypes,
            )
            // For member functions (scoped names), call with
            // just the member name (implicit this)
            val callTarget = when (funcName) {
                is Cpp.ScopedName -> funcName.member.deepCopy()
                is Cpp.SingleName -> funcName.deepCopy()
            }
            val body = cpp.blockStmt(
                listOf(
                    cpp.returnStmt(
                        cpp.callExpr(callTarget, callArgs),
                    ),
                ),
            )
            val def = cpp.funcDef(
                retType.deepCopy(),
                funcName.deepCopy(),
                overloadParams,
                body,
            )
            result.add(decl to def)
        }
        return result
    }

    fun translateModule(mod: TmpL.Module): List<Backend.TranslatedFileSpecification> {
        includes.clear()
        currentModuleLocation = mod.codeLocation.codeLocation
        preprocessImports(mod)

        fun namespaced(body: Iterable<Cpp.Global>): Iterable<Cpp.Global> = cpp.pos(mod) {
            val innerNamespace = when (cppLibraryName) {
                null -> body
                else -> listOf(cpp.namespace(cpp.libraryName(cppLibraryName), body))
            }
            val finalNamespace = cpp.namespace(cpp.singleName(CppName("temper")), innerNamespace)
            return@pos listOf(finalNamespace)
        }

        val relPath = mod.codeLocation.codeLocation.relativePath()

        val path = when {
            relPath.isFile -> relPath
            relPath.segments.isEmpty() -> filePath(INIT_NAME)
            else -> relPath.dirName().resolveFile(relPath.last().fullName)
        }

        return cpp.pos(mod) {
            val headerTypeDecl = mutableListOf<Cpp.Global>()
            val headerTypeDefs = mutableListOf<Cpp.Global>()
            val headerFunctions = mutableListOf<Cpp.Global>()
            val headerDecl = mutableListOf<Cpp.Global>()
            val headerInit = mutableListOf<Cpp.Global>()

            fun header(): List<Cpp.Global> = buildList {
                addAll(headerTypeDecl)
                addAll(headerTypeDefs)
                addAll(headerDecl) // before functions so template functions can see extern vars
                addAll(headerFunctions)
                addAll(headerInit)
            }

            val impl = mutableListOf<Cpp.Global>()

            // Pre-scan: check if module has template functions.
            // If so, non-exported module-level variables need extern
            // declarations in the header (template function bodies
            // in the header can reference them).
            val hasTemplateFunctions = mod.topLevels.any { tl ->
                tl is TmpL.ModuleFunctionDeclaration &&
                    tl.typeParameters.ot.typeParameters.isNotEmpty()
            }

            mod.topLevels.forEach { topLevel ->
                cpp.pos(topLevel) {
                    when (topLevel) {
                        is TmpL.EmbeddedComment -> {}
                        is TmpL.ModuleInitBlock -> {
                            // Use C++ struct-initialization pattern:
                            // A struct with a constructor that runs the init code,
                            // and a global instance that triggers construction.
                            val initName = cpp.tmp("Init")
                            val instanceName = cpp.tmp("init_instance")
                            val structDef = cpp.structDef(
                                initName,
                                listOf(
                                    cpp.funcDef(
                                        ret = null,
                                        name = initName,
                                        args = listOf(),
                                        body = translateBlock(topLevel.body),
                                    ),
                                ),
                            )
                            val varDef = cpp.varDef(initName, instanceName)
                            impl.add(structDef)
                            impl.add(varDef)
                        }
                        is TmpL.ModuleFunctionDeclaration -> {
                            val formals = topLevel.parameters.parameters
                            val hasOptional = formals.any { it.optional }
                            val typeFormals =
                                topLevel.typeParameters.ot.typeParameters
                            // Populate type formal map BEFORE translating
                            // return type, param types, and body
                            val savedTypeFormalNames = mutableMapOf<lang.temper.type.TypeDefinition, Cpp.SingleName>()
                            val savedTypeFormalKeys = mutableListOf<String>()
                            for (formal in typeFormals) {
                                val cppName = cpp.name(formal.name)
                                savedTypeFormalNames[formal.definition] = cppName
                                typeFormalNames[formal.definition] = cppName
                                val key = typeFormalKey(formal.definition)
                                typeFormalNamesByText[key] = cppName
                                savedTypeFormalKeys.add(key)
                            }
                            val paramTypes = formals.map {
                                translateParamType(it)
                            }
                            val restParam =
                                topLevel.parameters.restParameter
                            val allParamTypes = if (restParam != null) {
                                val elemType =
                                    translateType(restParam.type)
                                paramTypes + cpp.template(
                                    cpp.name(
                                        TEMPER_CORE_NAMESPACE,
                                        "List",
                                    ),
                                    listOf(elemType),
                                )
                            } else {
                                paramTypes
                            }
                            val allParamNames = if (restParam != null) {
                                formals.map { cpp.name(it.name) } +
                                    cpp.name(restParam.name)
                            } else {
                                formals.map { cpp.name(it.name) }
                            }
                            val func = cpp.func(
                                cpp.name(topLevel.name),
                                translateType(topLevel.returnType),
                                allParamTypes,
                                allParamNames,
                                translateBlock(topLevel.body),
                            )
                            // Clean up type formal names after body translation
                            for (formal in typeFormals) {
                                typeFormalNames.remove(formal.definition)
                            }
                            for (key in savedTypeFormalKeys) {
                                typeFormalNamesByText.remove(key)
                            }
                            if (typeFormals.isNotEmpty()) {
                                val templateParams = typeFormals.map { formal ->
                                    cpp.funcParam(
                                        cpp.singleName(
                                            CppName("class", allowKey = true),
                                        ),
                                        savedTypeFormalNames[formal.definition]
                                            ?: cpp.name(formal.name),
                                    )
                                }
                                // Template functions: full definition in header
                                headerFunctions.add(
                                    cpp.templateFuncDef(templateParams, func.def),
                                )
                                if (hasOptional) {
                                    val funcName = cpp.name(topLevel.name)
                                    val retType =
                                        translateType(topLevel.returnType)
                                    for (
                                    (_, def) in generateOptionalOverloads(
                                        funcName,
                                        retType,
                                        formals.toList(),
                                    )
                                    ) {
                                        headerFunctions.add(
                                            cpp.templateFuncDef(
                                                templateParams.map {
                                                    it.deepCopy()
                                                },
                                                def,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                headerFunctions.add(func.decl)
                                impl.add(func.def)
                                if (hasOptional) {
                                    val funcName = cpp.name(topLevel.name)
                                    val retType =
                                        translateType(topLevel.returnType)
                                    for ((decl, def) in generateOptionalOverloads(
                                        funcName,
                                        retType,
                                        formals.toList(),
                                    )) {
                                        headerFunctions.add(decl)
                                        impl.add(def)
                                    }
                                }
                            }
                        }
                        is TmpL.ModuleLevelDeclaration -> {
                            // Skip declarations for imported names — they're
                            // resolved to the source module's namespace.
                            val nameKey = cpp.name(topLevel.name).id.text
                            if (nameKey in importedNames) {
                                // noop: imported
                            } else {
                                val isReal = when (val type = topLevel.type.ot) {
                                    is TmpL.FunctionType -> true
                                    is TmpL.TypeIntersection -> false
                                    is TmpL.TypeUnion -> true
                                    is TmpL.GarbageType -> false
                                    is TmpL.NominalType -> when (type.typeName.sourceDefinition) {
                                        WellKnownTypes.voidType.definition -> false
                                        else -> true
                                    }
                                    is TmpL.BubbleType -> false
                                    is TmpL.NeverType -> false
                                    is TmpL.TopType -> true
                                }
                                if (isReal) {
                                    val type = translateType(topLevel.type)
                                    val name = cpp.name(topLevel.name)
                                    val isExported = topLevel.name.name is ExportedName
                                    val initExpr = topLevel.init
                                    val translatedInit = if (
                                        initExpr != null &&
                                        isAnyValueTmpLType(topLevel.type.ot) &&
                                        isValueType(initExpr.type)
                                    ) {
                                        cpp.callExpr(
                                            cpp.name(TEMPER_CORE_NAMESPACE, "any_box"),
                                            listOf(translateExpression(initExpr)),
                                        )
                                    } else {
                                        translateExpressionOrNull(initExpr)
                                    }
                                    if (isExported || hasTemplateFunctions) {
                                        // Exported or module has template functions:
                                        // extern declaration in header, definition in cpp.
                                        // Template functions in the header need to see
                                        // module-level variables.
                                        headerDecl.add(cpp.varDecl(type, name))
                                        impl.add(
                                            cpp.varDef(type, name, translatedInit),
                                        )
                                    } else {
                                        // Non-exported: static in cpp only, no header decl
                                        impl.add(
                                            cpp.varDef(
                                                Cpp.DefMod.Static, type, name, translatedInit,
                                            ),
                                        )
                                    }
                                }
                            } // end if not imported
                        }
                        is TmpL.TypeDeclaration -> {
                            val isInterface = topLevel.kind == TmpL.TypeDeclarationKind.Interface
                            // Populate type formal names for template struct
                            val structTypeFormals = topLevel.typeParameters.ot.typeParameters
                            val isTemplate = structTypeFormals.isNotEmpty()
                            // For template types, method defs go in header after struct,
                            // wrapped with template<class T> prefix
                            val templateMethodDefs = mutableListOf<Cpp.Global>()
                            val structTypeFormalKeys = mutableListOf<String>()
                            for (formal in structTypeFormals) {
                                val cppName = cpp.name(formal.name)
                                typeFormalNames[formal.definition] = cppName
                                val key = typeFormalKey(formal.definition)
                                typeFormalNamesByText[key] = cppName
                                structTypeFormalKeys.add(key)
                            }
                            // Pre-populate propertyDotNames so method bodies
                            // can resolve backing field names regardless of
                            // member ordering in TmpL.
                            for (member in topLevel.members) {
                                when (member) {
                                    is TmpL.Property -> {
                                        propertyDotNames[propKey(member.name.name)] = member.dotName.dotNameText
                                    }
                                    is TmpL.Getter -> {
                                        if (member.propertyShape.abstractness != Abstractness.Concrete) {
                                            val getterDotName = member.dotName.dotNameText
                                            val propDotName = getterDotName.removePrefix("get.")
                                            propertyDotNames[propKey(member.name.name)]?.let { } ?: run {
                                                propertyDotNames[propKey(member.name.name)] = propDotName
                                            }
                                            val methodName = if (getterDotName.contains('.')) {
                                                getterDotName.replace('.', '_')
                                            } else {
                                                "get_$getterDotName"
                                            }
                                            val getterCppName = cpp.singleName(
                                                CppName(fixName(methodName)),
                                            )
                                            getterMethodNames[propDotName] = getterCppName
                                        }
                                    }
                                    else -> {}
                                }
                            }
                            val declaredSetters = mutableSetOf<String>()
                            val structFields = buildList<Cpp.StructPart> {
                                // Add virtual destructor for interfaces to enable dynamic_pointer_cast
                                if (isInterface) {
                                    val dtorName = "~${cpp.name(topLevel.name).id.text}"
                                    add(
                                        cpp.funcDef(
                                            null,
                                            null,
                                            cpp.singleName(CppName("virtual", allowKey = true)),
                                            cpp.singleName(CppName(dtorName, raw = true)),
                                            emptyList(),
                                            cpp.blockStmt(emptyList()),
                                        ),
                                    )
                                }
                                for (member in topLevel.members) {
                                    cpp.pos(member) {
                                        when (member) {
                                            is TmpL.StaticProperty -> {
                                                propertyDotNames[propKey(member.name.name)] = member.dotName.dotNameText
                                                val propCppName = CppName(fixName(member.dotName.dotNameText))
                                                val fmtHints = CppFormattingHints.getInstance()
                                                val typeStr = toStringViaTokenSink(
                                                    formattingHints = fmtHints,
                                                    singleLine = true,
                                                ) { translateType(member.type).renderTo(it) }
                                                // Declare as static in struct using raw StructField
                                                add(
                                                    cpp.structField(
                                                        cpp.singleName(CppName("static $typeStr", raw = true)),
                                                        cpp.singleName(propCppName),
                                                    ),
                                                )
                                                // Define outside the struct in the .cpp
                                                impl.add(
                                                    Cpp.VarDef(
                                                        cpp.pos,
                                                        type = translateType(member.type),
                                                        name = cpp.scopedName(
                                                            cpp.name(topLevel.name),
                                                            cpp.singleName(propCppName),
                                                        ),
                                                        init = member.expression?.let { translateExpression(it) },
                                                    ),
                                                )
                                            }
                                            is TmpL.Property -> {
                                                propertyDotNames[propKey(member.name.name)] = member.dotName.dotNameText
                                                val propShape = member.memberShape as? PropertyShape
                                                if (propShape?.abstractness != Abstractness.Abstract) {
                                                    add(
                                                        cpp.structField(
                                                            translateType(member.type),
                                                            cpp.singleName(
                                                                CppName(fixName(member.dotName.dotNameText)),
                                                            ),
                                                        ),
                                                    )
                                                }
                                            }
                                            is TmpL.Getter -> {
                                                // Generate getter for concrete (backed) properties
                                                // Always generate to ensure GetAbstractProperty
                                                // finds the method regardless of type hierarchy
                                                if (member.propertyShape.abstractness == Abstractness.Concrete) {
                                                    val needsVirtual = isInterface ||
                                                        topLevel.superTypes.any()
                                                    // Generate getter that returns backing field
                                                    val getterDotName = member.dotName.dotNameText
                                                    // Ensure getter method name differs from field name
                                                    val methodName = if (getterDotName.contains('.')) {
                                                        getterDotName.replace('.', '_')
                                                    } else {
                                                        "get_$getterDotName"
                                                    }
                                                    val getterCppName = cpp.singleName(
                                                        CppName(fixName(methodName)),
                                                    )
                                                    val propDotName = getterDotName.removePrefix("get.")
                                                    getterMethodNames[propDotName] = getterCppName
                                                    propertyDotNames[propKey(member.name.name)]?.let { } ?: run {
                                                        propertyDotNames[propKey(member.name.name)] = propDotName
                                                    }
                                                    val backingFieldName = cpp.singleName(
                                                        CppName(fixName(propDotName)),
                                                    )
                                                    val func = cpp.func(
                                                        cpp.scopedName(
                                                            cpp.name(topLevel.name),
                                                            getterCppName,
                                                        ),
                                                        translateType(member.returnType),
                                                        emptyList(),
                                                        cpp.blockStmt(
                                                            listOf(
                                                                cpp.returnStmt(
                                                                    cpp.op(
                                                                        "->",
                                                                        cpp.literal(cpp.raw("this")),
                                                                        backingFieldName,
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                    )
                                                    if (isTemplate) {
                                                        val convention = if (needsVirtual) {
                                                            cpp.singleName(
                                                                CppName("virtual", allowKey = true),
                                                            )
                                                        } else {
                                                            null
                                                        }
                                                        add(
                                                            cpp.funcDecl(
                                                                func.decl.ret,
                                                                convention,
                                                                func.decl.name,
                                                                func.decl.args,
                                                            ),
                                                        )
                                                        templateMethodDefs.add(func.def)
                                                    } else {
                                                        impl.add(func.def)
                                                        val convention = if (needsVirtual) {
                                                            cpp.singleName(
                                                                CppName("virtual", allowKey = true),
                                                            )
                                                        } else {
                                                            null
                                                        }
                                                        add(
                                                            cpp.funcDecl(
                                                                func.decl.ret,
                                                                convention,
                                                                func.decl.name,
                                                                func.decl.args,
                                                            ),
                                                        )
                                                    }
                                                } else {
                                                    // Abstract getter — use get_ prefix to avoid
                                                    // field/method name collision in subtypes
                                                    val getterDotName = member.dotName.dotNameText
                                                    val methodName = if (getterDotName.contains('.')) {
                                                        getterDotName.replace('.', '_')
                                                    } else {
                                                        "get_$getterDotName"
                                                    }
                                                    val getterCppName = cpp.singleName(
                                                        CppName(fixName(methodName)),
                                                    )
                                                    val propDotName = getterDotName.removePrefix("get.")
                                                    getterMethodNames[propDotName] = getterCppName
                                                    propertyDotNames[propKey(member.name.name)]?.let { } ?: run {
                                                        propertyDotNames[propKey(member.name.name)] = propDotName
                                                    }
                                                    when (val body = member.body) {
                                                        null -> {
                                                            // Pure virtual getter
                                                            add(
                                                                cpp.funcDef(
                                                                    null,
                                                                    translateType(member.returnType),
                                                                    cpp.singleName(CppName("virtual", allowKey = true)),
                                                                    getterCppName,
                                                                    emptyList(),
                                                                    cpp.blockStmt(
                                                                        listOf(
                                                                            cpp.exprStmt(
                                                                                cpp.callExpr(
                                                                                    cpp.name(
                                                                                        TEMPER_CORE_NAMESPACE,
                                                                                        "pure_virtual",
                                                                                    ),
                                                                                ),
                                                                            ),
                                                                        ),
                                                                    ),
                                                                ),
                                                            )
                                                        }
                                                        else -> {
                                                            val func = cpp.func(
                                                                cpp.scopedName(
                                                                    cpp.name(topLevel.name),
                                                                    getterCppName,
                                                                ),
                                                                translateType(member.returnType),
                                                                member.parameters.parameters.drop(1).map { param ->
                                                                    cpp.pos(param) {
                                                                        val type = translateType(param.type)
                                                                        val name = cpp.name(param.name)
                                                                        type to name
                                                                    }
                                                                },
                                                                translateBlockWithThis(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.parameters.parameters.first().name),
                                                                    body,
                                                                ),
                                                            )
                                                            val needsVirtualGetter = isInterface ||
                                                                topLevel.superTypes.any()
                                                            if (isTemplate) {
                                                                if (needsVirtualGetter) {
                                                                    add(
                                                                        cpp.funcDecl(
                                                                            func.decl.ret,
                                                                            cpp.singleName(
                                                                                CppName("virtual", allowKey = true),
                                                                            ),
                                                                            func.decl.name,
                                                                            func.decl.args,
                                                                        ),
                                                                    )
                                                                } else {
                                                                    add(func.decl)
                                                                }
                                                                templateMethodDefs.add(func.def)
                                                            } else {
                                                                impl.add(func.def)
                                                                if (needsVirtualGetter) {
                                                                    add(
                                                                        cpp.funcDecl(
                                                                            func.decl.ret,
                                                                            cpp.singleName(
                                                                                CppName("virtual", allowKey = true),
                                                                            ),
                                                                            func.decl.name,
                                                                            func.decl.args,
                                                                        ),
                                                                    )
                                                                } else {
                                                                    add(func.decl)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } // end else (abstract getter)
                                            }
                                            is TmpL.Setter -> {
                                                if (member.propertyShape.abstractness == Abstractness.Concrete) {
                                                    if (isInterface || topLevel.superTypes.any()) {
                                                        // Generate an override setter that does direct field assignment
                                                        val setterDotName = member.dotName.dotNameText
                                                        val setterName = if (setterDotName.contains('.')) {
                                                            setterDotName.replace('.', '_')
                                                        } else {
                                                            "set_$setterDotName"
                                                        }
                                                        val setterCppName = cpp.singleName(
                                                            CppName(fixName(setterName)),
                                                        )
                                                        val propDotName =
                                                            member.dotName.dotNameText.removePrefix("set.")
                                                        setterMethodNames[propDotName] = setterCppName
                                                        val setterKey = setterCppName.id.text
                                                        val valueParams = member.parameters.parameters.drop(1)
                                                        val backingFieldName = cpp.singleName(
                                                            CppName(fixName(propDotName)),
                                                        )
                                                        val func = cpp.func(
                                                            cpp.scopedName(
                                                                cpp.name(topLevel.name),
                                                                setterCppName,
                                                            ),
                                                            translateType(member.returnType),
                                                            valueParams.map { param ->
                                                                cpp.pos(param) {
                                                                    val type = translateType(param.type)
                                                                    val name = cpp.name(param.name)
                                                                    type to name
                                                                }
                                                            },
                                                            cpp.blockStmt(
                                                                listOf(
                                                                    cpp.exprStmt(
                                                                        cpp.op(
                                                                            "=",
                                                                            cpp.op(
                                                                                "->",
                                                                                cpp.literal(cpp.raw("this")),
                                                                                backingFieldName,
                                                                            ),
                                                                            cpp.name(valueParams.first().name),
                                                                        ),
                                                                    ),
                                                                ),
                                                            ),
                                                        )
                                                        if (isTemplate) {
                                                            if (setterKey !in declaredSetters) {
                                                                declaredSetters.add(setterKey)
                                                                add(
                                                                    cpp.funcDecl(
                                                                        func.decl.ret,
                                                                        cpp.singleName(
                                                                            CppName("virtual", allowKey = true),
                                                                        ),
                                                                        func.decl.name,
                                                                        func.decl.args,
                                                                    ),
                                                                )
                                                            }
                                                            templateMethodDefs.add(func.def)
                                                        } else {
                                                            impl.add(func.def)
                                                            if (setterKey !in declaredSetters) {
                                                                declaredSetters.add(setterKey)
                                                                add(
                                                                    cpp.funcDecl(
                                                                        func.decl.ret,
                                                                        cpp.singleName(
                                                                            CppName("virtual", allowKey = true),
                                                                        ),
                                                                        func.decl.name,
                                                                        func.decl.args,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                    // else: no supertypes, backed properties use direct field access
                                                } else {
                                                    // Abstract setter — use set_ prefix to avoid
                                                    // field/method name collision in subtypes
                                                    val setterDotName = member.dotName.dotNameText
                                                    val setterName = if (setterDotName.contains('.')) {
                                                        setterDotName.replace('.', '_')
                                                    } else {
                                                        "set_$setterDotName"
                                                    }
                                                    val setterCppName = cpp.singleName(
                                                        CppName(fixName(setterName)),
                                                    )
                                                    val propDotName = setterDotName.removePrefix("set.")
                                                    setterMethodNames[propDotName] = setterCppName
                                                    val setterKey = setterCppName.id.text
                                                    when (val body = member.body) {
                                                        null -> {
                                                            // Pure virtual setter
                                                            val paramTypes = member.parameters.parameters.drop(1).map {
                                                                translateType(it.type)
                                                            }
                                                            if (setterKey !in declaredSetters) {
                                                                declaredSetters.add(setterKey)
                                                                add(
                                                                    cpp.funcDef(
                                                                        null,
                                                                        translateType(member.returnType),
                                                                        cpp.singleName(
                                                                            CppName("virtual", allowKey = true),
                                                                        ),
                                                                        setterCppName,
                                                                        paramTypes.mapIndexed { i, t ->
                                                                            cpp.funcParam(
                                                                                t,
                                                                                cpp.singleName(CppName("arg_$i")),
                                                                            )
                                                                        },
                                                                        cpp.blockStmt(
                                                                            listOf(
                                                                                cpp.exprStmt(
                                                                                    cpp.callExpr(
                                                                                        cpp.name(
                                                                                            TEMPER_CORE_NAMESPACE,
                                                                                            "pure_virtual",
                                                                                        ),
                                                                                    ),
                                                                                ),
                                                                            ),
                                                                        ),
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                        else -> {
                                                            val func = cpp.func(
                                                                cpp.scopedName(
                                                                    cpp.name(topLevel.name),
                                                                    setterCppName,
                                                                ),
                                                                translateType(member.returnType),
                                                                member.parameters.parameters.drop(1).map { param ->
                                                                    cpp.pos(param) {
                                                                        val type = translateType(param.type)
                                                                        val name = cpp.name(param.name)
                                                                        type to name
                                                                    }
                                                                },
                                                                translateBlockWithThis(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.parameters.parameters.first().name),
                                                                    body,
                                                                ),
                                                            )
                                                            val needsVirtualSetter = isInterface ||
                                                                topLevel.superTypes.any()
                                                            if (isTemplate) {
                                                                if (setterKey !in declaredSetters) {
                                                                    declaredSetters.add(setterKey)
                                                                    if (needsVirtualSetter) {
                                                                        add(
                                                                            cpp.funcDecl(
                                                                                func.decl.ret,
                                                                                cpp.singleName(
                                                                                    CppName("virtual", allowKey = true),
                                                                                ),
                                                                                func.decl.name,
                                                                                func.decl.args,
                                                                            ),
                                                                        )
                                                                    } else {
                                                                        add(func.decl)
                                                                    }
                                                                }
                                                                templateMethodDefs.add(func.def)
                                                            } else {
                                                                impl.add(func.def)
                                                                if (setterKey !in declaredSetters) {
                                                                    declaredSetters.add(setterKey)
                                                                    if (needsVirtualSetter) {
                                                                        add(
                                                                            cpp.funcDecl(
                                                                                func.decl.ret,
                                                                                cpp.singleName(
                                                                                    CppName("virtual", allowKey = true),
                                                                                ),
                                                                                func.decl.name,
                                                                                func.decl.args,
                                                                            ),
                                                                        )
                                                                    } else {
                                                                        add(func.decl)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                } // end if not concrete setter
                                            }
                                            is TmpL.NormalMethod -> {
                                                // If this method overrides a supertype getter/setter,
                                                // use the overridden dotName for the C++ method name
                                                // so it matches the interface's virtual method name.
                                                // E.g., interface has "get.something" → "get_something",
                                                // but concrete override has dotName "getsomething".
                                                val overriddenDotName = member.overridden
                                                    .firstOrNull()?.name?.dotNameText
                                                val effectiveDotName = if (
                                                    overriddenDotName != null &&
                                                    overriddenDotName.contains('.')
                                                ) {
                                                    overriddenDotName.replace('.', '_')
                                                } else {
                                                    member.dotName.dotNameText
                                                }
                                                val methodCppName0 = cpp.singleName(
                                                    CppName(fixName(effectiveDotName)),
                                                )
                                                when (val body = member.body) {
                                                    null -> {
                                                        // Pure virtual method
                                                        val paramTypes = member.parameters.parameters.drop(1).map {
                                                            translateType(it.type)
                                                        }
                                                        add(
                                                            cpp.funcDef(
                                                                null,
                                                                translateType(member.returnType),
                                                                cpp.singleName(
                                                                    CppName("virtual", allowKey = true),
                                                                ),
                                                                methodCppName0,
                                                                paramTypes.mapIndexed { i, t ->
                                                                    cpp.funcParam(
                                                                        t,
                                                                        cpp.singleName(CppName("arg_$i")),
                                                                    )
                                                                },
                                                                cpp.blockStmt(
                                                                    listOf(
                                                                        cpp.exprStmt(
                                                                            cpp.callExpr(
                                                                                cpp.name(
                                                                                    TEMPER_CORE_NAMESPACE,
                                                                                    "pure_virtual",
                                                                                ),
                                                                            ),
                                                                        ),
                                                                    ),
                                                                ),
                                                            ),
                                                        )
                                                    }
                                                    else -> {
                                                        val methodCppName = cpp.singleName(
                                                            CppName(fixName(effectiveDotName)),
                                                        )
                                                        val methodFormals =
                                                            member.parameters.parameters.drop(1)
                                                        val hasOptional =
                                                            methodFormals.any { it.optional }
                                                        // Methods need 'virtual' for
                                                        // polymorphic dispatch in C++
                                                        val needsVirtual = isInterface ||
                                                            topLevel.superTypes.any()
                                                        val func = cpp.func(
                                                            cpp.scopedName(
                                                                cpp.name(topLevel.name),
                                                                methodCppName,
                                                            ),
                                                            translateType(member.returnType),
                                                            methodFormals.map { param ->
                                                                cpp.pos(param) {
                                                                    translateParamType(param) to
                                                                        cpp.name(param.name)
                                                                }
                                                            },
                                                            translateBlockWithThis(
                                                                cpp.name(topLevel.name),
                                                                cpp.name(member.parameters.parameters.first().name),
                                                                body,
                                                            ),
                                                        )
                                                        if (isTemplate) {
                                                            // Template: decl in struct, def after
                                                            // struct with template prefix
                                                            if (needsVirtual) {
                                                                add(
                                                                    cpp.funcDecl(
                                                                        func.decl.ret,
                                                                        cpp.singleName(
                                                                            CppName("virtual", allowKey = true),
                                                                        ),
                                                                        func.decl.name,
                                                                        func.decl.args,
                                                                    ),
                                                                )
                                                            } else {
                                                                add(func.decl)
                                                            }
                                                            templateMethodDefs.add(func.def)
                                                        } else {
                                                            impl.add(func.def)
                                                            if (needsVirtual) {
                                                                add(
                                                                    cpp.funcDecl(
                                                                        func.decl.ret,
                                                                        cpp.singleName(
                                                                            CppName("virtual", allowKey = true),
                                                                        ),
                                                                        func.decl.name,
                                                                        func.decl.args,
                                                                    ),
                                                                )
                                                            } else {
                                                                add(func.decl)
                                                            }
                                                        }
                                                        if (hasOptional) {
                                                            val scopedName = cpp.scopedName(
                                                                cpp.name(topLevel.name),
                                                                methodCppName.deepCopy(),
                                                            )
                                                            val retType =
                                                                translateType(member.returnType)
                                                            for ((decl, def) in generateOptionalOverloads(
                                                                scopedName,
                                                                retType,
                                                                methodFormals.toList(),
                                                            )) {
                                                                if (isTemplate) {
                                                                    templateMethodDefs.add(def)
                                                                } else {
                                                                    add(decl)
                                                                    impl.add(def)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            is TmpL.StaticMethod -> {
                                                when (val body = member.body) {
                                                    null -> {
                                                        // Abstract static method — skip
                                                    }
                                                    else -> {
                                                        val methodCppName = cpp.singleName(
                                                            CppName(fixName(member.dotName.dotNameText)),
                                                        )
                                                        val methodFormals =
                                                            member.parameters.parameters
                                                        val hasOptional =
                                                            methodFormals.any { it.optional }
                                                        val func = cpp.func(
                                                            cpp.scopedName(
                                                                cpp.name(topLevel.name),
                                                                methodCppName,
                                                            ),
                                                            translateType(member.returnType),
                                                            methodFormals.map { param ->
                                                                cpp.pos(param) {
                                                                    translateParamType(param) to
                                                                        cpp.name(param.name)
                                                                }
                                                            },
                                                            translateBlock(body),
                                                        )
                                                        if (isTemplate) {
                                                            add(
                                                                cpp.funcDecl(
                                                                    Cpp.DefMod.Static,
                                                                    func.decl.ret,
                                                                    func.decl.name,
                                                                    func.decl.args,
                                                                ),
                                                            )
                                                            templateMethodDefs.add(func.def)
                                                        } else {
                                                            impl.add(func.def)
                                                            add(
                                                                cpp.funcDecl(
                                                                    Cpp.DefMod.Static,
                                                                    func.decl.ret,
                                                                    func.decl.name,
                                                                    func.decl.args,
                                                                ),
                                                            )
                                                        }
                                                        if (hasOptional) {
                                                            val scopedName = cpp.scopedName(
                                                                cpp.name(topLevel.name),
                                                                methodCppName.deepCopy(),
                                                            )
                                                            val retType =
                                                                translateType(member.returnType)
                                                            for ((decl, def) in generateOptionalOverloads(
                                                                scopedName,
                                                                retType,
                                                                methodFormals.toList(),
                                                                declMod = Cpp.DefMod.Static,
                                                            )) {
                                                                if (isTemplate) {
                                                                    templateMethodDefs.add(def)
                                                                } else {
                                                                    add(decl)
                                                                    impl.add(def)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            is TmpL.Constructor -> {
                                                val thisParam = member.parameters.parameters.first()
                                                val thisName = cpp.name(thisParam.name)
                                                val typeName = cpp.name(topLevel.name)
                                                val resultName = cpp.tmp("result")
                                                val makeName = cpp.singleName(CppName("make"))
                                                val constructorFormals =
                                                    member.parameters.parameters.drop(1)
                                                val params = constructorFormals.map {
                                                    cpp.pos(it) {
                                                        val baseType = translateType(it.type)
                                                        val paramType = if (it.optional) {
                                                            cpp.template(
                                                                cpp.name(
                                                                    TEMPER_CORE_NAMESPACE,
                                                                    "NullableParam",
                                                                ),
                                                                listOf(baseType),
                                                            )
                                                        } else {
                                                            baseType
                                                        }
                                                        cpp.funcParam(
                                                            paramType,
                                                            cpp.name(it.name),
                                                        )
                                                    }
                                                }
                                                val paramTypes = constructorFormals.map {
                                                    cpp.pos(it) {
                                                        val baseType = translateType(it.type)
                                                        if (it.optional) {
                                                            cpp.template(
                                                                cpp.name(
                                                                    TEMPER_CORE_NAMESPACE,
                                                                    "NullableParam",
                                                                ),
                                                                listOf(baseType),
                                                            )
                                                        } else {
                                                            baseType
                                                        }
                                                    }
                                                }
                                                val objectType = cpp.template(
                                                    cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                                                    listOf(typeName),
                                                )
                                                val body = cpp.pos(member.body) {
                                                    cpp.blockStmt(
                                                        buildList {
                                                            add(cpp.varDef(typeName, resultName))
                                                            add(
                                                                cpp.varDef(
                                                                    cpp.ptr(typeName),
                                                                    thisName,
                                                                    cpp.literal(
                                                                        cpp.raw(
                                                                            "&${resultName.id.text}",
                                                                        ),
                                                                    ),
                                                                ),
                                                            )
                                                            member.body.statements.forEach { stmt ->
                                                                if (stmt is TmpL.ReturnStatement) {
                                                                    return@forEach
                                                                }
                                                                addAll(translateStatement(stmt))
                                                            }
                                                            add(
                                                                cpp.returnStmt(
                                                                    cpp.callExpr(
                                                                        cpp.template(
                                                                            cpp.name(
                                                                                "std",
                                                                                "make_shared",
                                                                            ),
                                                                            listOf(typeName),
                                                                        ),
                                                                        listOf(
                                                                            resultName.deepCopy(),
                                                                        ),
                                                                    ),
                                                                ),
                                                            )
                                                        },
                                                    )
                                                }
                                                val isTemplateStruct =
                                                    topLevel.typeParameters.ot
                                                        .typeParameters.isNotEmpty()
                                                val hasOptionalParams =
                                                    constructorFormals.any { it.optional }
                                                if (isTemplateStruct) {
                                                    // Template struct: must inline make()
                                                    add(
                                                        cpp.funcDef(
                                                            Cpp.DefMod.Static,
                                                            objectType,
                                                            makeName,
                                                            params,
                                                            body,
                                                        ),
                                                    )
                                                } else {
                                                    // Non-template struct: declare in header,
                                                    // define in .cpp to avoid scope issues
                                                    add(
                                                        cpp.funcDecl(
                                                            Cpp.DefMod.Static,
                                                            objectType,
                                                            makeName,
                                                            paramTypes,
                                                        ),
                                                    )
                                                    impl.add(
                                                        cpp.funcDef(
                                                            objectType,
                                                            cpp.scopedName(
                                                                typeName,
                                                                makeName.deepCopy(),
                                                            ),
                                                            params,
                                                            body,
                                                        ),
                                                    )
                                                    if (hasOptionalParams) {
                                                        val scopedMake = cpp.scopedName(
                                                            typeName.deepCopy(),
                                                            makeName.deepCopy(),
                                                        )
                                                        for ((decl, def) in generateOptionalOverloads(
                                                            scopedMake,
                                                            objectType.deepCopy(),
                                                            constructorFormals.toList(),
                                                            declMod = Cpp.DefMod.Static,
                                                        )) {
                                                            add(decl)
                                                            impl.add(def)
                                                        }
                                                    }
                                                }
                                            }
                                            is TmpL.GarbageStatement -> {
                                                add(cpp.comment("TODO: TmpL.GarbageStatement"))
                                            }
                                        }
                                    }
                                }
                            }
                            val structName = cpp.name(topLevel.name)
                            val struct = cpp.struct(structName, structFields)
                            val superTypes = topLevel.superTypes.toList()
                            val typeFormals = topLevel.typeParameters.ot.typeParameters
                            if (typeFormals.isNotEmpty()) {
                                val templateParams = typeFormals.map { formal ->
                                    cpp.funcParam(
                                        cpp.singleName(CppName("class", allowKey = true)),
                                        cpp.name(formal.name),
                                    )
                                }
                                // For template structs, skip the forward declaration
                                // and emit the full template struct definition
                                val structDefToUse = if (superTypes.isNotEmpty()) {
                                    // Template + inheritance: inject base class into struct name
                                    val fmtHints = CppFormattingHints.getInstance()
                                    val baseStr = toStringViaTokenSink(
                                        formattingHints = fmtHints,
                                        singleLine = true,
                                    ) {
                                        translateSuperType(superTypes.first()).renderTo(it)
                                    }
                                    val rawName = "${structName.id.text} : public $baseStr"
                                    cpp.structDef(
                                        cpp.singleName(CppName(rawName, raw = true)),
                                        structFields,
                                    )
                                } else if (isInterface) {
                                    // Template interface with no supertypes — inherit from AnyValueBase
                                    val rawName = "${structName.id.text} : public temper::core::AnyValueBase"
                                    cpp.structDef(
                                        cpp.singleName(CppName(rawName, raw = true)),
                                        structFields,
                                    )
                                } else {
                                    // No supertypes
                                    cpp.structDef(structName, structFields)
                                }
                                headerTypeDefs.add(
                                    cpp.templateStructDef(templateParams, structDefToUse),
                                )
                                // Emit out-of-line template method definitions after the struct.
                                // The func.def uses ClassName::method() scoping,
                                // but we need ClassName<T1,T2>::method() for templates.
                                if (templateMethodDefs.isNotEmpty()) {
                                    val typeParamStr = typeFormals.joinToString(", ") { formal ->
                                        cpp.name(formal.name).id.text
                                    }
                                    for (methodDef in templateMethodDefs) {
                                        if (methodDef is Cpp.FuncDef) {
                                            // Rewrite scoped name to include template params
                                            val fixedDef = when (val defName = methodDef.name) {
                                                is Cpp.ScopedName -> {
                                                    val parentText = when (val p = defName.parent) {
                                                        is Cpp.SingleName -> p.id.text
                                                        is Cpp.ScopedName -> p.member.id.text
                                                        else -> structName.id.text
                                                    }
                                                    val rawParent = "$parentText<$typeParamStr>"
                                                    cpp.funcDef(
                                                        methodDef.mod,
                                                        methodDef.ret,
                                                        methodDef.convention,
                                                        cpp.scopedName(
                                                            cpp.singleName(CppName(rawParent, raw = true)),
                                                            defName.member.deepCopy(),
                                                        ),
                                                        methodDef.args.toList(),
                                                        methodDef.body,
                                                    )
                                                }
                                                else -> methodDef
                                            }
                                            headerTypeDefs.add(
                                                cpp.templateFuncDef(templateParams, fixedDef),
                                            )
                                        }
                                    }
                                }
                            } else if (superTypes.isNotEmpty()) {
                                // Class with inheritance
                                val baseType = translateSuperType(superTypes.first())
                                val baseName = baseType as? Cpp.Name
                                if (baseName != null) {
                                    headerTypeDecl.add(struct.decl)
                                    headerTypeDefs.add(
                                        cpp.derivedStructDef(structName, baseName, structFields),
                                    )
                                } else {
                                    // Base type has template params — use raw name hack
                                    val fmtHints = CppFormattingHints.getInstance()
                                    val baseStr = toStringViaTokenSink(
                                        formattingHints = fmtHints,
                                        singleLine = true,
                                    ) { baseType.renderTo(it) }
                                    val rawName = "${structName.id.text} : public $baseStr"
                                    headerTypeDecl.add(struct.decl)
                                    headerTypeDefs.add(
                                        cpp.structDef(
                                            cpp.singleName(CppName(rawName, raw = true)),
                                            structFields,
                                        ),
                                    )
                                }
                            } else if (isInterface) {
                                // Interface with no supertypes — inherit from AnyValueBase
                                // to allow dynamic_pointer_cast for AnyValue conversions
                                headerTypeDecl.add(struct.decl)
                                headerTypeDefs.add(
                                    cpp.derivedStructDef(
                                        structName,
                                        cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase"),
                                        structFields,
                                    ),
                                )
                            } else {
                                // No supertypes
                                headerTypeDecl.add(struct.decl)
                                headerTypeDefs.add(struct.def)
                            }
                            // Clean up type formal names after type declaration
                            for (formal in structTypeFormals) {
                                typeFormalNames.remove(formal.definition)
                            }
                            for (key in structTypeFormalKeys) {
                                typeFormalNamesByText.remove(key)
                            }
                        }
                        is TmpL.TypeConnection -> {
                            // Type connections are handled by the support network
                        }
                        is TmpL.PooledValueDeclaration -> {
                            // Pooled values are inlined at use sites
                        }
                        is TmpL.SupportCodeDeclaration -> {
                            // Support code declarations are handled inline
                        }
                        is TmpL.Test -> {
                            // Tests are translated as functions
                            val cppName = cpp.name(topLevel.name)
                            val func = cpp.func(
                                cppName,
                                translateType(topLevel.returnType),
                                topLevel.parameters.parameters.map { translateType(it.type) },
                                topLevel.parameters.parameters.map { cpp.name(it.name) },
                                translateBlock(topLevel.body),
                            )
                            headerFunctions.add(func.decl)
                            impl.add(func.def)
                            // Collect test info for main.cpp generation
                            val libNs = cppLibraryName?.let {
                                cpp.libraryName(it).id.text
                            }
                            val qualifiedName = when (libNs) {
                                null -> "temper::${cppName.id.text}"
                                else -> "temper::${libNs}::${cppName.id.text}"
                            }
                            testInfos.add(qualifiedName to topLevel.rawName)
                        }
                        is TmpL.GarbageTopLevel -> {
                            // Skip garbage
                        }
                        is TmpL.BoilerplateCodeFoldBoundary -> {
                            // Skip boilerplate markers
                        }
                    }
                }
            }

            val modPath = mod.codeLocation.outputPath
            val hppName = path.withTemperAwareExtension(HPP_EXT)

            listOf(
                Backend.TranslatedFileSpecification(
                    path = path.withTemperAwareExtension(HPP_EXT),
                    mimeType = MimeType.cppSource,
                    content = cpp.includeGuard(
                        cpp.tmp("TEMPER_HEADER_GUARD"),
                        cpp.program(
                            buildList {
                                add(cpp.include("temper-core/core.hpp"))
                                for (inc in includes.sorted()) {
                                    add(cpp.include(inc))
                                }
                                addAll(namespaced(header()))
                            },
                        ),
                    ),
                ),
                Backend.TranslatedFileSpecification(
                    path = path.withTemperAwareExtension(CPP_EXT),
                    mimeType = MimeType.cppSource,
                    content = cpp.program(
                        buildList {
                            add(cpp.include("$modPath$hppName"))
                            addAll(namespaced(impl))
                        },
                    ),
                ),
            )
        }
    }
}
