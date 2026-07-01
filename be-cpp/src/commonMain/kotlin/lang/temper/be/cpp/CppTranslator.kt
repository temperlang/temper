package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.mapParameters
import lang.temper.be.tmpl.referencedNames
import lang.temper.common.MimeType
import lang.temper.common.ignore
import lang.temper.common.subListToEnd
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.FilePath
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
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.WellKnownTypes
import lang.temper.type2.NullableType
import lang.temper.type2.Nullity
import lang.temper.type2.Type2
import lang.temper.type2.TypeCategory
import lang.temper.type2.sigForFunInterfaceType
import lang.temper.type2.typeCategory
import lang.temper.type2.withNullity
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TString

/** How the C++ backend renders Temper function types. Emitted and matched in one place. */
private const val STD_FUNCTION_PREFIX = "std::function"

/**
 * Translates a TmpL module into C++ source files.
 *
 * Constructed once per module by [CppBackend.translate]. The translator is stateful:
 * mutable maps track type formals, property names, narrowing contexts, and import
 * resolution across the module's top-level declarations. All per-module state is
 * cleared at the start of [translateModule].
 *
 * The generated C++ follows a header/source split: type declarations, function
 * declarations, and extern variable declarations go in the .hpp; function
 * definitions, static variables, and the init function go in the .cpp. Module-level
 * variable forward declarations are emitted before class method definitions to
 * prevent forward-reference errors.
 */
class CppTranslator(
    cppNames: CppNames,
    private val cppLibraryName: String? = null,
    libraryRootToOutputDir: Map<FilePath, String> = emptyMap(),
    /**
     * Member dotNames (methods and getters) whose receiver may be mutated (transitively),
     * from [lang.temper.be.tmpl.mutatingMemberNames]. A member whose dotName is absent
     * does not mutate `this` and is emitted `const`. Keyed by dotName so the decision is
     * uniform across an override slot, keeping const-ness consistent between an
     * interface's declaration and its concrete overrides.
     */
    private val mutatingMethodNames: Set<String> = emptySet(),
) {
    internal val cpp = CppBuilder(cppNames, libraryRootToOutputDir)
    private val includes = mutableSetOf<String>()
    private var currentModuleLocation: ModuleName? = null

    /**
     * Maps internal property names to their external dotName text.
     * Populated during TypeDeclaration processing so property accesses use consistent names.
     * Uses CppName text as key (derived from ResolvedName) for reliable matching
     * since SourceName uses identity comparison.
     */
    private val propertyDotNames = mutableMapOf<String, String>()

    /** Stable string key for a ResolvedName, based on the C++ name it would generate. */
    private fun propKey(name: ResolvedName): String = cpp.name(name).id.text

    /** Maps (typeName, propertyDotName) to getter method C++ name for abstract properties. */
    private val getterMethodNames = mutableMapOf<String, Cpp.SingleName>()

    /** Maps propertyDotName to setter method C++ name. */
    private val setterMethodNames = mutableMapOf<String, Cpp.SingleName>()

    /** Test info collected during translation: (C++ function name, raw test display name). */
    internal val testInfos = mutableListOf<Pair<String, String>>()

    /** Module init function name (set during translation, used by CppBackend). */
    internal var moduleInitFuncName: String? = null

    private var localFuncRefCaptures = mutableListOf<String>()

    /**
     * Names of mutable (`var`) locals currently in scope. Generated closures capture
     * by value (`[=]`) so that closures escaping their defining scope (e.g. stored in a
     * collection and invoked later) safely copy any captured values and shared_ptrs.
     * Mutable locals and local-function names instead need by-reference capture: the
     * former so writes propagate to the enclosing scope, the latter so (mutually)
     * recursive local functions can refer to themselves. This set accumulates across
     * nested blocks and is saved/restored at block boundaries.
     */
    private var mutableLocalsInScope = mutableSetOf<String>()

    /** Names of local variables that have void type (can't be declared in C++). */
    private val voidVarNames = mutableSetOf<String>()

    /**
     * Narrowing context: maps C++ variable names to their narrowed C++ types.
     * Pushed when entering an IfStatement whose condition is an InstanceOfExpression,
     * so property accesses and assignments use the narrowed type via dynamic_pointer_cast.
     */
    private val narrowingContext = mutableMapOf<String, Cpp.Type>()

    /** Current "this" variable name in member function bodies (for coercion on return). */
    private var currentThisVarName: String? = null

    /**
     * Maps TypeFormal definitions to their C++ template parameter names.
     * Populated before translating generic function/type bodies so that type
     * references within the body use the same names as the template declaration.
     * Keyed by TypeDefinition identity AND by name text for fallback matching,
     * since TmpL may use different TypeFormal instances for the same logical
     * type parameter (e.g., in localized imports).
     */
    private val typeFormalNames = lang.temper.common.mutableIdentityMapOf<TypeDefinition, Cpp.SingleName>()
    private val typeFormalNamesByText = mutableMapOf<String, Cpp.SingleName>()

    /** Generate a key for matching type formals by base name text.
     *  Different SourceName instances for the same type parameter have the same
     *  baseName.nameText but different uids, so we match on the base name only. */
    private fun typeFormalKey(def: TypeDefinition): String {
        val name = def.name
        return when (name) {
            is SourceName -> "tf:${name.baseName.nameText}"
            is Temporary -> "tf:${name.nameHint}"
            is ExportedName -> "tf:${name.baseName.nameText}"
            is BuiltinName -> "tf:${name.builtinKey}"
        }
    }

    /**
     * Maps C++ name text of imported names to their source module and external name.
     * Keyed on CppName text since SourceName uses identity comparison.
     */
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
    fun resolveTypeName(def: TypeDefinition): Cpp.Name {
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
    private fun isValueType(type: Type2): Boolean = isValueTypeDef(type.definition)

    /**
     * If [subject] is a TmpL.Reference whose variable is in the narrowing context,
     * wrap the translated expression with static_pointer_cast to the narrowed type.
     * Otherwise return the translated expression as-is.
     */
    private fun translateWithNarrowing(subject: TmpL.Expression): Cpp.Expr {
        val translated = translateExpression(subject)
        if (subject is TmpL.Reference) {
            val varName = cpp.name(subject.id).id.text
            val narrowedType = narrowingContext[varName]
            if (narrowedType != null) {
                return cpp.callExpr(
                    cpp.template(
                        cpp.name("std", "dynamic_pointer_cast"),
                        listOf(narrowedType),
                    ),
                    listOf(translated),
                )
            }
        }
        return translated
    }

    private val pointerCastFunctionNames = setOf(
        "dynamic_pointer_cast", "static_pointer_cast", "checked_cast", "temper_upcast",
    )

    /**
     * True if [expr] is already a pointer cast whose target element type's leaf name is
     * [targetLeaf]. Used to avoid re-casting a value that is already of the target type
     * (e.g. a reference narrowed by an enclosing `is` check), which otherwise produces
     * redundant nested casts.
     */
    private fun isPointerCastToLeaf(expr: Cpp.Expr, targetLeaf: String?): Boolean {
        if (targetLeaf == null) return false
        val call = expr as? Cpp.CallExpr ?: return false
        val func = call.func as? Cpp.TemplateType ?: return false
        if (leafNameOf(func.base) !in pointerCastFunctionNames) return false
        val arg0 = func.args.firstOrNull() ?: return false
        return leafNameOf(arg0) == targetLeaf
    }

    private fun leafNameOf(name: Cpp.Type): String? = when (name) {
        is Cpp.SingleName -> name.id.text
        is Cpp.ScopedName -> (name.formatElement(1) as? Cpp.SingleName)?.id?.text
        else -> null
    }

    /**
     * Analyze a translated C++ if-condition to detect `dynamic_pointer_cast<T>(x) != nullptr`
     * patterns. Returns (variable name, narrowed inner type) if found.
     */
    private fun extractNarrowingFromCppExpr(cppExpr: Cpp.Expr): Pair<String, Cpp.Type>? {
        // Pattern: BinaryExpr(CallExpr(TemplateType(dynamic_pointer_cast, [T]), [x]), !=, nullptr)
        if (cppExpr !is Cpp.BinaryExpr) return null
        val castCall = cppExpr.left as? Cpp.CallExpr ?: return null
        val templateExpr = castCall.func as? Cpp.TemplateType ?: return null
        if (leafNameOf(templateExpr.base) != "dynamic_pointer_cast") return null
        val narrowedType = templateExpr.args.firstOrNull() ?: return null
        val subjectArg = castCall.args.firstOrNull() ?: return null
        val varName = when (subjectArg) {
            is Cpp.SingleName -> subjectArg.id.text
            else -> renderToString(subjectArg)
        }
        return Pair(varName, narrowedType)
    }

    /**
     * Wraps a translated expression with list_upcast if the target type is a list
     * with a different element type than the source expression's list type.
     * Handles C++ invariant vectors: List<Derived> -> List<Base> requires explicit conversion.
     */
    private fun wrapWithListUpcastIfNeeded(
        translatedExpr: Cpp.Expr,
        initExprType: Type2?,
        declaredTmpLType: Type2?,
    ): Cpp.Expr {
        if (initExprType == null || declaredTmpLType == null) return translatedExpr
        // Only List and Listed take part in covariant element upcasts here; ListBuilder is
        // intentionally excluded (it is mutable, so element-type covariance is unsound), which
        // is why this is a narrower set than the class-wide collectionTypeDefs / listLikeDefs.
        val declDef = declaredTmpLType.definition
        val initDef = initExprType.definition
        val listUpcastDefs = setOf(
            WellKnownTypes.listTypeDefinition,
            WellKnownTypes.listedTypeDefinition,
        )
        if (declDef !in listUpcastDefs || initDef !in listUpcastDefs) return translatedExpr
        // Check if element types differ
        val declElem = declaredTmpLType.bindings.firstOrNull() ?: return translatedExpr
        val initElem = initExprType.bindings.firstOrNull() ?: return translatedExpr
        if (declElem.definition == initElem.definition) return translatedExpr
        // Element types differ — wrap with List::upcast<DeclaredElemType>
        val declElemCpp = translateType2(declElem)
        return cpp.callExpr(
            cpp.template(
                cpp.name(TEMPER_CORE_NAMESPACE, "List", "upcast"),
                listOf(declElemCpp),
            ),
            listOf(translatedExpr),
        )
    }

    /**
     * Adapts a translated call argument to its parameter type: covariant list
     * conversion (`List<Derived>` → `List<Base>`, which C++ vectors don't do
     * implicitly) followed by a class up/down cast where needed.
     */
    private fun wrapArgForParam(
        translated: Cpp.Expr,
        actualType: Type2?,
        paramType: Type2?,
    ): Cpp.Expr {
        val listAdjusted = wrapWithListUpcastIfNeeded(translated, actualType, paramType)
        return wrapWithNarrowingCastIfNeeded(listAdjusted, actualType, paramType)
    }

    /**
     * Wraps a translated expression with explicit static_pointer_cast when the source
     * and target types differ and both are class/interface types (shared_ptr-wrapped).
     * Handles both upcasts (derived→base) and downcasts (base→derived) since C++
     * shared_ptr conversions may be ambiguous in diamond inheritance hierarchies.
     */
    private fun wrapWithNarrowingCastIfNeeded(
        translatedExpr: Cpp.Expr,
        sourceType: Type2?,
        targetType: Type2?,
    ): Cpp.Expr {
        if (sourceType == null || targetType == null) return translatedExpr
        val srcDef = sourceType.definition
        val tgtDef = targetType.definition
        if (srcDef == tgtDef) return translatedExpr
        if (isValueTypeDef(srcDef) || isValueTypeDef(tgtDef)) return translatedExpr
        // Skip AnyValue/Void targets
        if (tgtDef == WellKnownTypes.anyValueTypeDefinition) return translatedExpr
        if (tgtDef == WellKnownTypes.voidTypeDefinition) return translatedExpr
        if (srcDef == WellKnownTypes.voidTypeDefinition) return translatedExpr
        // Skip collection types (List, ListBuilder, Map, Deque, etc.)
        if (srcDef in collectionTypeDefs || tgtDef in collectionTypeDefs) return translatedExpr
        // Skip Never/Bubble types and function types
        if (srcDef == WellKnownTypes.neverTypeDefinition) return translatedExpr
        if (srcDef == WellKnownTypes.functionTypeDefinition ||
            tgtDef == WellKnownTypes.functionTypeDefinition
        ) {
            return translatedExpr
        }
        // Only cast when the target type translates to Object<T> (a shared_ptr<T>)
        val fullTargetType = translateType2(targetType)
        val innerType = if (fullTargetType is Cpp.TemplateType) {
            // Must be Object<T> specifically, not Function<...> or other templates
            val baseName = leafNameOf(fullTargetType.base)
            if (baseName != "shared_ptr") return translatedExpr
            val inner = fullTargetType.args.firstOrNull() ?: return translatedExpr
            if (inner is Cpp.TemplateType) return translatedExpr
            inner
        } else {
            return translatedExpr
        }
        // Also verify the source type translates to Object<T>
        val fullSourceType = translateType2(sourceType)
        if (fullSourceType is Cpp.TemplateType) {
            val srcBaseName = leafNameOf(fullSourceType.base)
            if (srcBaseName != "shared_ptr") return translatedExpr
            // Skip only when source and target translate to the *same* C++ type, i.e. the
            // cast would be a no-op. Compare the fully-rendered type (which includes any
            // namespace qualification), not just the leaf name: two distinct types that
            // share a simple name (e.g. `a::Node` vs `b::Node`) must still be cast, since
            // `srcDef == tgtDef` (the same-definition case) was already excluded above.
            val srcInner = fullSourceType.args.firstOrNull()
            if (srcInner != null && renderToString(srcInner) == renderToString(innerType)) {
                return translatedExpr
            }
        } else {
            return translatedExpr
        }
        // Skip if the cast target is a type formal (not a concrete struct we generated):
        // casting to a template parameter before instantiation is meaningless. Detect the
        // formal structurally from the definition (every non-formal shape — value types,
        // collections, applied generics, functions — was already excluded above). This used
        // to be a regex on the rendered name, which both misfired on all-uppercase user type
        // names (`HTTP`, `URL`, `UUID`) and missed formals not registered in `typeFormalNames`
        // (e.g. coroutine `YIELD` formals), emitting a bogus upcast for the latter.
        val innerLeaf = leafNameOf(innerType)
        if (tgtDef is TypeFormal) return translatedExpr
        // Don't re-cast a value already cast to this type (avoids redundant nested casts).
        if (isPointerCastToLeaf(translatedExpr, innerLeaf)) return translatedExpr
        return cpp.callExpr(
            cpp.template(
                cpp.name(TEMPER_CORE_NAMESPACE, "temper_upcast"),
                listOf(innerType),
            ),
            listOf(translatedExpr),
        )
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

    private fun accessorMethodName(dotName: String, prefix: String): String =
        if (dotName.contains('.')) dotName.replace('.', '_') else "${prefix}_$dotName"

    private fun accessorSingleName(dotName: String, prefix: String): Cpp.SingleName =
        cpp.singleName(CppName(fixName(accessorMethodName(dotName, prefix = prefix))))

    private fun getterSingleName(dotName: String): Cpp.SingleName =
        accessorSingleName(dotName = dotName, prefix = "get")

    private fun setterSingleName(dotName: String): Cpp.SingleName =
        accessorSingleName(dotName = dotName, prefix = "set")

    /**
     * The primitive value types that can be implicitly converted between one another (used by
     * [isValueTypeMismatch] to decide when an explicit cast is needed). This is deliberately
     * narrower than [isValueTypeDef]: void and the stringIndex family are value types for
     * representation purposes but are not numeric/string-convertible, so they must not appear here.
     */
    private val valueTypeDefs = setOf(
        WellKnownTypes.intTypeDefinition,
        WellKnownTypes.int64TypeDefinition,
        WellKnownTypes.float64TypeDefinition,
        WellKnownTypes.booleanTypeDefinition,
        WellKnownTypes.stringTypeDefinition,
    )

    private fun isValueTypeMismatch(declDef: TypeDefinition?, exprDef: TypeDefinition?): Boolean =
        declDef in valueTypeDefs && exprDef in valueTypeDefs && declDef != exprDef

    private fun isTypeMismatch(declaredType: TmpL.Type, expr: TmpL.Expression): Boolean {
        val declDef = when (declaredType) {
            is TmpL.NominalType -> declaredType.typeName.sourceDefinition
            else -> return false
        }
        return isValueTypeMismatch(declDef, expr.type.definition)
    }

    private fun isTypeMismatch2(declaredType: Type2, expr: TmpL.Expression): Boolean =
        isValueTypeMismatch(declaredType.definition, expr.type.definition)

    private fun renderToString(tree: Cpp.Tree): String =
        toStringViaTokenSink(CppFormattingHints.getInstance(), singleLine = true) { tree.renderTo(it) }

    private fun sharedPtr(inner: Cpp.Type): Cpp.Type =
        cpp.template(cpp.name("std", "shared_ptr"), inner)

    private fun stdVector(elemType: Cpp.Type): Cpp.Type =
        cpp.template(cpp.name("std", "vector"), elemType)

    private val virtualConvention get() = cpp.singleName(CppName("virtual", allowKey = true))

    private fun MutableList<Cpp.StructPart>.emitMethodDeclAndDef(
        func: CppBuilder.Func,
        isTemplate: Boolean,
        needsVirtual: Boolean,
        impl: MutableList<Cpp.Global>,
        templateMethodDefs: MutableList<Cpp.Global>,
        declMod: Cpp.DefMod? = null,
        emitDecl: Boolean = true,
    ) {
        val convention = when {
            needsVirtual -> virtualConvention
            else -> null
        }
        val decl = if (declMod != null) {
            cpp.funcDecl(declMod, func.decl.ret, func.decl.name, func.decl.args)
        } else {
            cpp.funcDecl(func.decl.ret, convention, func.decl.name, func.decl.args, func.decl.qual)
        }
        if (emitDecl) add(decl)
        if (isTemplate) {
            templateMethodDefs.add(func.def)
        } else {
            impl.add(func.def)
        }
    }

    /**
     * Append the optional-parameter [overloads] produced by [generateOptionalOverloads] for an
     * instance or static method: template methods keep their definitions inline in
     * [templateMethodDefs]; plain methods emit a declaration into the struct (the receiver) and an
     * out-of-line definition into [impl].
     */
    private fun MutableList<Cpp.StructPart>.emitMethodOverloads(
        overloads: List<Pair<Cpp.FuncDecl, Cpp.FuncDef>>,
        isTemplate: Boolean,
        impl: MutableList<Cpp.Global>,
        templateMethodDefs: MutableList<Cpp.Global>,
    ) {
        for ((decl, def) in overloads) {
            if (isTemplate) {
                templateMethodDefs.add(def)
            } else {
                add(decl)
                impl.add(def)
            }
        }
    }

    private fun pureVirtualBody(): Cpp.BlockStmt = cpp.blockStmt(
        listOf(cpp.exprStmt(cpp.callExpr(cpp.name(TEMPER_CORE_NAMESPACE, "pure_virtual")))),
    )

    private fun pureVirtualMethod(
        retType: Cpp.Type,
        name: Cpp.SingleName,
        params: List<Cpp.FuncParam> = emptyList(),
        qual: Cpp.MethodQualifier? = null,
    ): Cpp.FuncDef = cpp.funcDef(null, retType, virtualConvention, name, params, pureVirtualBody(), qual = qual)

    /**
     * The `const` qualifier for a member (method or getter) that does not mutate its
     * receiver per the cross-member mutation analysis, or null otherwise. Keyed by the
     * member's dotName so the decision is identical across an override slot.
     */
    private fun memberConstQualifier(memberDotName: String): Cpp.MethodQualifier? =
        if (memberDotName in mutatingMethodNames) null else Cpp.MethodQualifier.Const

    private fun stdFunction(retType: Cpp.Type, paramTypes: List<Cpp.Type>): Cpp.Type {
        val retStr = renderToString(retType)
        val paramStr = paramTypes.joinToString(", ") { renderToString(it) }
        return cpp.singleName(CppName("$STD_FUNCTION_PREFIX<$retStr($paramStr)>", raw = true))
    }

    /** Translate a Type2 to a C++ type. Used by SupportNetwork inline code. */
    private fun cppBaseTypeForDefinition(def: TypeDefinition): Cpp.Type? = when (def) {
        WellKnownTypes.anyValueTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase")
        WellKnownTypes.intTypeDefinition -> cpp.type("int32_t")
        WellKnownTypes.int64TypeDefinition -> cpp.type("int64_t")
        WellKnownTypes.float64TypeDefinition -> cpp.type("double")
        WellKnownTypes.booleanTypeDefinition -> cpp.type("bool")
        WellKnownTypes.stringTypeDefinition -> cpp.name("std", "string")
        WellKnownTypes.voidTypeDefinition -> cpp.type("void")
        WellKnownTypes.stringIndexTypeDefinition -> cpp.type("int32_t")
        WellKnownTypes.stringIndexOptionTypeDefinition -> cpp.type("int32_t")
        WellKnownTypes.noStringIndexTypeDefinition -> cpp.type("int32_t")
        WellKnownTypes.neverTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Never")
        WellKnownTypes.invalidTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Invalid")
        WellKnownTypes.nullTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Null")
        WellKnownTypes.symbolTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Symbol")
        WellKnownTypes.typeTypeDefinition -> cpp.type("void*")
        WellKnownTypes.promiseTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Promise")
        WellKnownTypes.promiseBuilderTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "PromiseBuilder")
        WellKnownTypes.functionTypeDefinition -> cpp.type("$STD_FUNCTION_PREFIX<void()>")
        WellKnownTypes.listTypeDefinition -> cpp.name("std", "vector")
        WellKnownTypes.listedTypeDefinition -> cpp.name("std", "vector")
        WellKnownTypes.listBuilderTypeDefinition -> cpp.name("std", "vector")
        WellKnownTypes.mapTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Mapped", "Ordered")
        WellKnownTypes.mapBuilderTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Mapped", "Ordered")
        WellKnownTypes.mappedTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Mapped", "Ordered")
        WellKnownTypes.dequeTypeDefinition -> cpp.name("std", "deque")
        else -> null
    }

    private val listLikeDefs = setOf(
        WellKnownTypes.listTypeDefinition,
        WellKnownTypes.listBuilderTypeDefinition,
        WellKnownTypes.listedTypeDefinition,
    )
    private val mapLikeDefs = setOf(
        WellKnownTypes.mapTypeDefinition,
        WellKnownTypes.mapBuilderTypeDefinition,
        WellKnownTypes.mappedTypeDefinition,
    )

    /**
     * Every collection-like type definition (list-like, map-like, and deque). Single source for
     * "is this a collection?" checks so the membership stays in sync with [listLikeDefs] and
     * [mapLikeDefs]. Note this is deliberately broader than the upcast-only `listDefs` set in
     * [wrapWithListUpcastIfNeeded], which excludes ListBuilder.
     */
    private val collectionTypeDefs: Set<TypeDefinition> =
        listLikeDefs + mapLikeDefs + WellKnownTypes.dequeTypeDefinition

    /** True when [def] is an in-scope type parameter (type formal). */
    private fun isTypeParameterDef(def: TypeDefinition?): Boolean {
        if (def == null) return false
        return typeFormalNames[def] != null || typeFormalNamesByText[typeFormalKey(def)] != null
    }

    fun translateType2(type: Type2): Cpp.Type {
        if (type is NullableType && type.definition != WellKnownTypes.nullTypeDefinition) {
            val nonNullType = type.withNullity(Nullity.NonNull)
            val inner = translateType2(nonNullType)
            // Value types and type parameters cannot themselves hold null, so a
            // nullable form needs an explicit optional wrapper. Concrete reference
            // types are already nullable as `shared_ptr`. A type parameter's nullness
            // is not known until instantiation, so it must use NullableParam to work
            // for both value- and reference-typed arguments (e.g. OrNullJsonAdapter<Int>).
            if (isValueType(nonNullType) || isTypeParameterDef(nonNullType.definition)) {
                return cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "NullableParam"), inner)
            }
            return inner
        }
        val def = type.definition
        if (
            def != WellKnownTypes.functionTypeDefinition &&
            def.typeCategory == TypeCategory.Functional &&
            type is lang.temper.type2.DefinedType
        ) {
            val sig = sigForFunInterfaceType(type)
            if (sig != null) {
                val retType = translateType2(sig.returnType2)
                val paramTypes = sig.allValueFormals.map { translateType2(it.type) }
                return stdFunction(retType, paramTypes)
            }
        }
        val bindings = type.bindings
        val typeArgs = bindings.map { translateType2(it) }
        if (typeArgs.isNotEmpty()) {
            when {
                def == WellKnownTypes.functionTypeDefinition ->
                    return stdFunction(typeArgs.last(), typeArgs.dropLast(1))
                def in listLikeDefs ->
                    return sharedPtr(stdVector(typeArgs.first()))
                def in mapLikeDefs ->
                    return sharedPtr(cpp.template(cppBaseTypeForDefinition(def)!!, typeArgs))
                def == WellKnownTypes.dequeTypeDefinition ->
                    return sharedPtr(cpp.template(cppBaseTypeForDefinition(def)!!, typeArgs))
            }
        }
        cppBaseTypeForDefinition(def)?.let { base ->
            if (def == WellKnownTypes.anyValueTypeDefinition) return sharedPtr(base)
            return base
        }
        (typeFormalNames[def] ?: typeFormalNamesByText[typeFormalKey(def)])?.let { return it }
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
        return sharedPtr(base)
    }

    private fun translateImplicitsType(builtinKey: String): Cpp.Type = when (builtinKey) {
        "AnyValue" -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase")
        "AnyValueBase" -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase")
        "Fn" -> cpp.type("$STD_FUNCTION_PREFIX<void()>")
        "Console" -> cpp.name(TEMPER_CORE_NAMESPACE, "Console", "Type")
        "StringBuilder" -> cpp.name("std", "ostringstream")
        "DenseBitVector" -> cpp.template(cpp.name("std", "vector"), cpp.type("bool"))
        "OrderedMap" -> cpp.name(TEMPER_CORE_NAMESPACE, "Mapped", "Ordered")
        "StringIndex" -> cpp.type("int32_t")
        "StringIndexOption" -> cpp.type("int32_t")
        "NoStringIndex" -> cpp.type("int32_t")
        "Type" -> cpp.type("void*")
        "Pair" -> cpp.name(TEMPER_CORE_NAMESPACE, "Pair")
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
        is TmpL.ConnectedToTypeName -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase")
        is TmpL.TemperTypeName -> {
            val def = name.typeDefinition
            cppBaseTypeForDefinition(def) ?: run {
                // Check if this is a type formal with a known template parameter name
                val typeFormalName = typeFormalNames[def] ?: typeFormalNamesByText[typeFormalKey(def)]
                if (typeFormalName != null) {
                    typeFormalName
                } else {
                    when (val loc = def.sourceLocation) {
                        ImplicitsCodeLocation -> when (val defName = def.name) {
                            is ExportedName -> translateImplicitsType(defName.baseName.builtinKey)
                            is SourceName -> translateImplicitsType(defName.baseName.builtinKey)
                            is Temporary -> translateImplicitsType(defName.nameHint)
                            is BuiltinName -> translateImplicitsType(defName.builtinKey)
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

    /** Translate a TmpL type to a C++ type expression. */
    internal fun translateType(type: TmpL.Type): Cpp.Type = cpp.pos(type) {
        if (inTranslateType.contains(type)) {
            return@pos sharedPtr(cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase"))
        }
        inTranslateType.add(type)
        try {
            when (type) {
                is TmpL.FunctionType -> {
                    val ret = translateType(type.returnType)
                    val params = type.valueFormals.formals.map { formal ->
                        translateType(formal.type)
                    }
                    stdFunction(ret, params)
                }
                is TmpL.TypeIntersection -> sharedPtr(cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase"))
                is TmpL.TypeUnion -> {
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
                            // A bubble contributes no value-carrying alternative to the union's
                            // C++ type; the bubble path is realized via coroutine lowering, not here.
                            is TmpL.BubbleType -> {}
                            else -> other.add(type)
                        }
                    }

                    type.types.forEach(::addType)

                    val base = if (other.size == 1) {
                        val first = translateType(other[0])
                        val innerDef = when (val ot = other[0]) {
                            is TmpL.NominalType -> ot.typeName.sourceDefinition
                            else -> null
                        }
                        // Value types and type parameters cannot hold null directly,
                        // so wrap in NullableParam. Concrete reference types are nullable
                        // as shared_ptr. A type parameter's nullness is unknown until
                        // instantiation (e.g. OrNullJsonAdapter<Int>), so it needs the wrapper.
                        if (isNull && (isValueTypeDef(innerDef) || isTypeParameterDef(innerDef))) {
                            cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "NullableParam"), first)
                        } else {
                            first
                        }
                    } else if (isNull) {
                        cpp.name(TEMPER_CORE_NAMESPACE, "Null")
                    } else {
                        cpp.type("void")
                    }

                    base
                }
                is TmpL.GarbageType -> cpp.name(TEMPER_CORE_NAMESPACE, "Never")
                is TmpL.NominalType -> {
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
                        val innerDef = when (val tn = type.typeName) {
                            is TmpL.TemperTypeName -> tn.typeDefinition
                            else -> null
                        }
                        val inner = if (type.params.isEmpty()) {
                            translateTypeName(type.typeName)
                        } else {
                            cpp.template(
                                translateTypeName(type.typeName),
                                type.params.map { param -> translateType(param) },
                            )
                        }
                        if (isValueTypeDef(innerDef)) {
                            inner
                        } else {
                            sharedPtr(inner)
                        }
                    }
                }
                is TmpL.BubbleType -> cpp.type("void")
                is TmpL.NeverType -> cpp.type("void")
                is TmpL.TopType -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase")
            }
        } finally {
            inTranslateType.removeLast()
        }
    }

    /**
     * Emits a placeholder for an expression-or-callable construct the C++ backend cannot
     * translate. Rather than silently producing nothing (which would compile to subtly wrong
     * behaviour) we emit a call to `std::abort()` carrying a comment that names the construct:
     * the gap is visible in the generated source, and any program that actually reaches it
     * fails loudly instead of misbehaving.
     */
    private fun unsupportedConstruct(value: TmpL.ExpressionOrCallable): Cpp.Expr = cpp.callExpr(
        cpp.name("std", "abort"),
    ).withComment("unsupported: ${value.javaClass}")

    /**
     * Statement-position counterpart to [unsupportedConstruct]: for statement kinds the backend
     * cannot lower (e.g. a `YieldStatement` that escaped the coroutine state-machine lowering, or
     * a `ModuleInitFailed`). Emits `std::abort();` with a naming comment so the construct is never
     * dropped without a trace.
     */
    private fun unsupportedStatement(value: TmpL.Statement): List<Cpp.Stmt> = listOf(
        cpp.exprStmt(
            cpp.callExpr(cpp.name("std", "abort")).withComment("unsupported: ${value.javaClass}"),
        ),
    )

    /**
     * For statement kinds that intentionally produce no C++ — source comments and documentation
     * fold markers. Unlike [unsupportedStatement] these are *meant* to vanish; the named helper
     * documents that the empty result is deliberate, not an oversight.
     */
    private fun noCodeFor(value: TmpL.Statement): List<Cpp.Stmt> {
        ignore(value)
        return emptyList()
    }

    private fun translateCallable(fn: TmpL.Callable): Cpp.Expr = cpp.pos(fn) {
        when (fn) {
            is TmpL.InlineSupportCodeWrapper -> unsupportedConstruct(fn)
            is TmpL.FnReference -> resolveNameCrossModule(fn.id)
            is TmpL.FunInterfaceCallable -> translateExpression(fn.expr)
            is TmpL.ConstructorReference -> cpp.scopedName(
                translateTypeName(fn.typeName),
                cpp.singleName(CppName("make")),
            )

            is TmpL.GarbageCallable -> unsupportedConstruct(fn)
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
                    is TmpL.SuperSubject -> cpp.op(
                        "->",
                        cpp.singleName(currentThisVarName!!),
                        cpp.scopedName(
                            translateTypeName(subject.typeName),
                            when ((fn.method as? MethodShape)?.methodKind) {
                                MethodKind.Normal -> cpp.singleName(CppName(fn.methodName.dotNameText))
                                MethodKind.Getter -> getterSingleName(fn.methodName.dotNameText)
                                MethodKind.Setter -> setterSingleName(fn.methodName.dotNameText)
                                else -> error("invalid method kind for super call")
                            },
                        ),
                    )
                }
        }
    }

    /** Translate a TmpL expression to a C++ expression. */
    // Dispatches each expression node to a dedicated translate* method, mirroring the
    // mature backends (e.g. Rust's translateExpression). The `cpp.pos(expr)` scope sets
    // the source position for the dynamic extent, so the delegated methods emit nodes at
    // the right position without each re-establishing it.
    private fun translateExpression(expr: TmpL.Expression): Cpp.Expr = cpp.pos(expr) {
        when (expr) {
            is TmpL.AwaitExpression -> unsupportedConstruct(expr)
            // Compile-time type errors generate a bubble at runtime.
            is TmpL.BubbleSentinel -> bubbleVoid()
            is TmpL.CallExpression -> translateCallExpression(expr)
            is TmpL.CastExpression -> translateCastExpression(expr)
            is TmpL.FunInterfaceExpression -> translateCallable(expr.callable)
            is TmpL.GarbageExpression -> unsupportedConstruct(expr)
            is TmpL.GetAbstractProperty -> translateGetAbstractProperty(expr)
            is TmpL.GetBackedProperty -> translateGetBackedProperty(expr)
            is TmpL.InstanceOfExpression -> translateInstanceOfExpression(expr)
            is TmpL.InfixOperation -> {
                val left = translateExpression(expr.left)
                val right = translateExpression(expr.right)
                cpp.op(expr.op.kind.outputToken.text, left, right)
            }
            is TmpL.PrefixOperation -> {
                cpp.unaryExpr(cpp.unaryOp(expr.op.kind.outputToken.text), translateExpression(expr.operand))
            }
            is TmpL.Reference -> translateReference(expr)
            is TmpL.RestParameterCountExpression -> cpp.callExpr(
                cpp.name(TEMPER_CORE_NAMESPACE, "List", "length"),
                listOf(cpp.name(expr.parameterName)),
            )
            is TmpL.RestParameterExpression -> cpp.callExpr(
                cpp.name(TEMPER_CORE_NAMESPACE, "List", "get"),
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
            is TmpL.ValueReference -> translateValueReference(expr)
        }
    }

    /** A runtime `bubble<void>()` call, emitted where a type error must fail at runtime. */
    private fun bubbleVoid(): Cpp.Expr = cpp.callExpr(
        cpp.template(
            cpp.name(TEMPER_CORE_NAMESPACE, "bubble"),
            listOf(cpp.type("void")),
        ),
        emptyList(),
    )

    private fun translateCallExpression(expr: TmpL.CallExpression): Cpp.Expr {
        return when (val fn = expr.fn) {
            is TmpL.GarbageCallable -> {
                unsupportedConstruct(fn)
            }

            is TmpL.InlineSupportCodeWrapper -> {
                when (val supportCode = fn.supportCode) {
                    is CppInlineSupportCode -> supportCode.inlineToTree(
                        expr.pos,
                        expr.mapParameters { actual, staticType, _ ->
                            val actualExpr = actual as? TmpL.Expression
                                ?: error("expected expression in inline support code args")
                            TypedArg(
                                translateExpression(actualExpr),
                                staticType ?: WellKnownTypes.anyValueType2,
                            )
                        },
                        expr.type,
                        this,
                    ) as? Cpp.Expr ?: error("inline support code did not produce an expression")

                    else -> unsupportedConstruct(expr)
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
                val ctxSig = expr.contextualizedSig
                val numRequired = sig.requiredInputTypes.size -
                    (if (sig.hasThisFormal) 1 else 0)
                val optionalTypes = sig.optionalInputTypes
                val ctorParamTypes = ctxSig.requiredInputTypes.drop(
                    if (sig.hasThisFormal) 1 else 0,
                )
                cpp.callExpr(
                    callable,
                    expr.parameters.mapIndexed { idx, actual ->
                        val actualExpr = actual as TmpL.Expression
                        val optionalIdx = idx - numRequired
                        val isOptionalParam = optionalIdx >= 0
                        val isNullLiteral = actualExpr is TmpL.ValueReference &&
                            actualExpr.value.typeTag == TNull
                        if (isOptionalParam && optionalIdx < optionalTypes.size) {
                            val optType = optionalTypes[optionalIdx]
                            if (isNullLiteral) {
                                wrapInNullableParamIfNeeded(optType)
                            } else {
                                wrapInNullableParamIfNeeded(
                                    optType,
                                    translateExpression(actualExpr),
                                )
                            }
                        } else {
                            val translated = translateExpression(actualExpr)
                            val paramType = ctorParamTypes.getOrNull(idx)
                            wrapArgForParam(
                                translated,
                                actualExpr.type,
                                paramType,
                            )
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
                    return bubbleVoid()
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
                val isSuperCall = (fn as? TmpL.MethodReference)?.subject is TmpL.SuperSubject
                val paramTypes = ctxSig.requiredInputTypes.drop(
                    if (sig.hasThisFormal || isSuperCall) 1 else 0,
                )
                val translatedArgs = mutableListOf<Cpp.Expr>()
                val parameters = when {
                    isSuperCall -> expr.parameters.subListToEnd(1) // called on (borrowed) `this`
                    else -> expr.parameters
                }
                for ((idx, actual) in parameters.withIndex()) {
                    if (actual is TmpL.RestSpread) {
                        translatedArgs.add(
                            cpp.name(actual.parameterName),
                        )
                        continue
                    }
                    val actualExpr = actual as TmpL.Expression
                    if (restType != null && idx >= numNonRest) {
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
                        val optType = optionalTypes[optionalIdx]
                        if (isNullLiteral) {
                            translatedArgs.add(
                                wrapInNullableParamIfNeeded(optType),
                            )
                        } else {
                            translatedArgs.add(
                                wrapInNullableParamIfNeeded(
                                    optType,
                                    translateExpression(actualExpr),
                                ),
                            )
                        }
                    } else {
                        val translated = translateExpression(actualExpr)
                        val paramType = paramTypes.getOrNull(idx)
                        translatedArgs.add(
                            wrapArgForParam(
                                translated,
                                actualExpr.type,
                                paramType,
                            ),
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
                                "List", "make",
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

    private fun translateCastExpression(expr: TmpL.CastExpression): Cpp.Expr {
        // Use checked_cast for shared_ptr types (throws on failure), static_cast for value types
        val targetType = translateType(expr.checkedType)
        val sourceExpr = translateExpression(expr.expr)
        val innerCheckedType = expr.checkedType.privOtOrNull
        val isCastToValueType = innerCheckedType != null &&
            innerCheckedType is TmpL.NominalType &&
            isValueTypeDef(innerCheckedType.typeName.sourceDefinition)
        return if (isCastToValueType) {
            // Casting to a value type — identity cast (value types can't be cast dynamically)
            sourceExpr
        } else if (targetType is Cpp.TemplateType) {
            val innerType = targetType.args.firstOrNull() ?: targetType
            if (isPointerCastToLeaf(sourceExpr, leafNameOf(innerType))) {
                // Subject is already cast to this type (e.g. narrowed by an `is`
                // check); don't add a redundant checked_cast.
                sourceExpr
            } else {
                cpp.callExpr(
                    cpp.template(
                        cpp.name(TEMPER_CORE_NAMESPACE, "checked_cast"),
                        listOf(innerType),
                    ),
                    listOf(sourceExpr),
                )
            }
        } else {
            // Bare type name or unrecognized form — identity cast
            // (avoid C-style casts which can silently reinterpret)
            sourceExpr
        }
    }

    private fun translateGetAbstractProperty(expr: TmpL.GetAbstractProperty): Cpp.Expr {
        // Abstract properties are accessed via getter methods
        val propDotName = when (val prop = expr.property) {
            is TmpL.ExternalPropertyId -> prop.name.dotNameText
            is TmpL.InternalPropertyId -> propertyDotNames[propKey(prop.name.name)] ?: prop.name.name.toString()
        }
        // Use narrowing-aware subject translation for type-checked variables
        val subjectExpr = translateWithNarrowing(expr.subject)
        val getterName = getterMethodNames[propDotName]
        return if (getterName != null) {
            cpp.callExpr(
                cpp.op("->", subjectExpr, getterName),
                listOf(),
            )
        } else if (expr.property is TmpL.ExternalPropertyId) {
            val inferredGetterName = cpp.singleName(
                CppName(fixName("get_$propDotName")),
            )
            cpp.callExpr(
                cpp.op("->", subjectExpr, inferredGetterName),
                listOf(),
            )
        } else {
            cpp.op("->", subjectExpr, translatePropertyId(expr.property))
        }
    }

    private fun translateGetBackedProperty(expr: TmpL.GetBackedProperty): Cpp.Expr {
        val propName = translatePropertyId(expr.property)
        return when (val subject = expr.subject) {
            is TmpL.Expression -> cpp.op("->", translateWithNarrowing(subject), propName)
            is TmpL.ConnectedToTypeName -> cpp.scopedName(translateTypeName(subject), propName)
            is TmpL.TemperTypeName -> cpp.scopedName(translateTypeName(subject), propName)
            is TmpL.SuperSubject -> error("illegal super call for backed property")
        }
    }

    private fun translateInstanceOfExpression(expr: TmpL.InstanceOfExpression): Cpp.Expr {
        val targetType = translateType(expr.checkedType)
        val sourceExpr = translateExpression(expr.expr)
        // Check if the checked type is a value type (Object<T> resolves to T, not shared_ptr<T>)
        val innerCheckedType = expr.checkedType.privOtOrNull
        val checkedTypeDef = when (innerCheckedType) {
            is TmpL.NominalType -> innerCheckedType.typeName.sourceDefinition
            else -> null
        }
        val isCheckedValueType = isValueTypeDef(checkedTypeDef)
        return if (isCheckedValueType) {
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

    private fun translateReference(expr: TmpL.Reference): Cpp.Expr {
        val resolved = resolveNameCrossModule(expr.id)
        val varName = cpp.name(expr.id).id.text
        val narrowedType = narrowingContext[varName]
        return if (narrowedType != null) {
            cpp.callExpr(
                cpp.template(
                    cpp.name("std", "dynamic_pointer_cast"),
                    listOf(narrowedType),
                ),
                listOf(resolved),
            )
        } else {
            resolved
        }
    }

    private fun translateValueReference(expr: TmpL.ValueReference): Cpp.Expr {
        val value = expr.value
        return when (value.typeTag) {
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
                // nullptr converts to: shared_ptr nullptr,
                // NullableParam has_value=false (for value types).
                // C++ runtime functions with optional params use NullableParam.
                cpp.literal(cpp.raw("nullptr"))
            }
            // The frontend already reported an error for this program, so it will not
            // be run; emit a benign placeholder purely so diagnostic codegen completes.
            TProblem -> cpp.literal(cpp.raw("/* error value */ 0"))
            else -> {
                val type = expr.type
                when (type.definition) {
                    WellKnownTypes.voidType.definition -> cpp.literal(cpp.raw("(void)0"))
                    WellKnownTypes.typeType.definition -> cpp.literal(
                        cpp.raw("0"),
                    )
                    // An unrecognised literal tag would otherwise be silently emitted as
                    // `0`, compiling into subtly wrong behaviour. Fail loudly at translation
                    // time instead so the gap is caught rather than shipped.
                    else -> error(
                        "C++ backend cannot translate literal with tag ${value.typeTag} " +
                            "(type ${type.definition})",
                    )
                }
            }
        }
    }

    private fun translateExpressionOrNull(expr: TmpL.Expression?): Cpp.Expr? = when (expr) {
        null -> null
        else -> translateExpression(expr)
    }

    /** Translate a TmpL statement to zero or more C++ statements. */
    private fun translateStatement(stmt: TmpL.Statement): Iterable<Cpp.Stmt> = cpp.pos(stmt) {
        when (stmt) {
            is TmpL.Assignment -> {
                // Skip assignments to imported names (they alias the external)
                val leftKey = cpp.name(stmt.left).id.text
                val isRhsVoid = stmt.right is TmpL.Expression &&
                    (stmt.right as TmpL.Expression).type.definition == WellKnownTypes.voidTypeDefinition
                if (leftKey in importedNames || leftKey in voidVarNames || isRhsVoid) {
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
                            is TmpL.HandlerScope -> cpp.callExpr(
                                cpp.name("std", "abort"),
                            ).withComment("unhandled: ${right.javaClass}")
                        }
                    }
                    // Wrap with list_upcast or narrowing cast if needed
                    val finalRight = if (right is TmpL.Expression) {
                        val rhsType = right.type
                        wrapWithListUpcastIfNeeded(
                            rightExpr, rhsType, stmt.type,
                        ).let { upcast ->
                            wrapWithNarrowingCastIfNeeded(upcast, rhsType, stmt.type)
                        }
                    } else {
                        rightExpr
                    }
                    listOf(
                        cpp.exprStmt(
                            cpp.op(
                                "=",
                                cpp.name(stmt.left),
                                finalRight,
                            ),
                        ),
                    )
                }
            }
            is TmpL.BoilerplateCodeFoldEnd -> noCodeFor(stmt)
            is TmpL.BoilerplateCodeFoldStart -> noCodeFor(stmt)
            is TmpL.BreakStatement -> when (val label = stmt.label) {
                null -> listOf(cpp.breakStmt())
                else -> {
                    val labelName = renderToString(cpp.name(label.id))
                    listOf(cpp.gotoStmt(cpp.singleName("${labelName}_end")))
                }
            }
            is TmpL.ContinueStatement -> when (val label = stmt.label) {
                null -> listOf(cpp.exprStmt(cpp.literal(cpp.raw("continue"))))
                else -> {
                    val labelName = renderToString(cpp.name(label.id))
                    listOf(cpp.gotoStmt(cpp.singleName(labelName)))
                }
            }
            is TmpL.EmbeddedComment -> noCodeFor(stmt)
            is TmpL.ExpressionStatement -> listOf(
                cpp.exprStmt(translateExpression(stmt.expression)),
            )

            is TmpL.GarbageStatement -> unsupportedStatement(stmt)
            is TmpL.HandlerScope -> {
                unsupportedStatement(stmt)
            }
            is TmpL.LocalDeclaration -> {
                if (!stmt.assignOnce) {
                    // Track mutable locals so nested closures capture them by reference.
                    mutableLocalsInScope.add(cpp.name(stmt.name).id.text)
                }
                val initExpr = stmt.init
                val innerType = stmt.type.privOtOrNull
                // Check if this is a void variable (can't declare void vars in C++)
                val isVoidVar = innerType is TmpL.NominalType &&
                    innerType.typeName.sourceDefinition == WellKnownTypes.voidTypeDefinition
                if (isVoidVar) {
                    voidVarNames.add(cpp.name(stmt.name).id.text)
                    if (initExpr != null) {
                        listOf(cpp.exprStmt(translateExpression(initExpr)))
                    } else {
                        emptyList()
                    }
                } else {
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
                    // Wrap with list_upcast if needed for covariant list conversion
                    val finalInit = if (translatedInit != null && initExpr != null) {
                        wrapWithListUpcastIfNeeded(
                            translatedInit,
                            initExpr.type,
                            stmt.descriptor,
                        )
                    } else {
                        translatedInit
                    }
                    listOf(
                        cpp.varDef(
                            translateType(stmt.type),
                            cpp.name(stmt.name),
                            finalInit,
                        ),
                    )
                }
            }

            is TmpL.LocalFunctionDeclaration -> {
                // Only emit the assignment here; the forward declaration
                // is hoisted by translateBlock to support mutual recursion.
                val params = stmt.parameters.parameters.map { param ->
                    cpp.funcParam(translateType(param.type), cpp.name(param.name))
                }
                // A coroutine state machine (produced by the coroutine→control-flow
                // lowering) takes the generator as its sole argument and is invoked
                // repeatedly via next(). Its persistent locals (case index, surviving
                // variables) must live across calls and outlive the wrapper that builds
                // it, so it captures them by value in a `mutable` lambda and owns them —
                // no heap indirection. Other closures capture by value too, but local
                // functions (recursion) and mutable locals (write-back to the enclosing
                // scope) need by-reference capture.
                val isCoroutineStateMachine = stmt.parameters.parameters.singleOrNull()
                    ?.type?.privOtOrNull
                    .let { it as? TmpL.NominalType }
                    ?.typeName?.sourceDefinition
                    .let {
                        it == WellKnownTypes.generatorTypeDefinition ||
                            it == WellKnownTypes.safeGeneratorTypeDefinition
                    }
                val byRefCandidates = if (isCoroutineStateMachine) {
                    localFuncRefCaptures
                } else {
                    localFuncRefCaptures + mutableLocalsInScope
                }
                // Determine which candidates the body actually references by walking the body
                // AST and rendering each referenced name the same way candidates are rendered,
                // rather than scanning the generated source text (which would spuriously match
                // an identifier appearing inside a string literal or comment).
                val referencedNamesInBody = stmt.body.referencedNames()
                    .map { renderToString(cpp.name(it)) }
                    .toSet()
                val byRef = byRefCandidates
                    .distinct()
                    .filter { name -> name in referencedNamesInBody }
                val lambda = cpp.lambda(
                    captures = byRef.map { cpp.lambdaCapture(cpp.singleName(it)) },
                    params = params,
                    mutable = isCoroutineStateMachine,
                    ret = translateType(stmt.returnType),
                    body = translateBlock(stmt.body),
                )
                listOf(
                    cpp.exprStmt(cpp.op("=", cpp.name(stmt.name), lambda)),
                )
            }
            is TmpL.ModuleInitFailed -> unsupportedStatement(stmt)
            is TmpL.BlockStatement -> listOf(translateBlock(stmt))

            is TmpL.ComputedJumpStatement -> {
                // Emit a C++ switch. The TmpL translator only produces these from
                // internal lowering (e.g. the coroutine state machine), never from
                // user `break`, so an unlabeled `break` safely exits the switch here.
                // Append a `break;` to a case body that doesn't already leave control,
                // to prevent fall-through.
                fun caseBody(body: TmpL.BlockStatement): Cpp.BlockStmt {
                    val block = translateBlock(body)
                    return if (blockEndsWithControlLeave(body)) {
                        block
                    } else {
                        cpp.blockStmt(block.stmts + cpp.breakStmt())
                    }
                }
                val cases = stmt.cases.map { case ->
                    cpp.switchCase(
                        case.values.map { cpp.caseLabel(cpp.literal(it.index)) },
                        caseBody(case.body),
                    )
                }
                listOf(
                    cpp.switchStmt(
                        translateExpression(stmt.caseExpr),
                        cases,
                        caseBody(stmt.elseCase.body),
                    ),
                )
            }
            is TmpL.IfStatement -> {
                // Translate condition first
                val testExpr = translateExpression(stmt.test)
                // Check if condition is a type check (dynamic_pointer_cast pattern)
                // to set up narrowing context for the consequent block
                val narrowKey = extractNarrowingFromCppExpr(testExpr)
                val consequentStmts = if (narrowKey != null) {
                    val savedNarrowing = narrowingContext[narrowKey.first]
                    narrowingContext[narrowKey.first] = narrowKey.second
                    val result = try {
                        translateStatement(stmt.consequent)
                    } finally {
                        if (savedNarrowing != null) {
                            narrowingContext[narrowKey.first] = savedNarrowing
                        } else {
                            narrowingContext.remove(narrowKey.first)
                        }
                    }
                    result
                } else {
                    translateStatement(stmt.consequent)
                }
                listOf(
                    cpp.ifStmt(
                        testExpr,
                        cpp.blockStmt(consequentStmts),
                        stmt.alternate?.let {
                            cpp.blockStmt(translateStatement(it))
                        },
                    ),
                )
            }

            is TmpL.LabeledStatement -> {
                val body = translateStatement(stmt.statement).toList()
                val labelName = cpp.name(stmt.label.id)
                val labelText = renderToString(labelName)
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
            is TmpL.TryStatement -> listOf(
                cpp.tryCatch(
                    cpp.blockStmt(translateStatement(stmt.tried).toList()),
                    cpp.blockStmt(translateStatement(stmt.recover).toList()),
                ),
            )
            is TmpL.WhileStatement -> listOf(
                cpp.whileStmt(
                    translateExpression(stmt.test),
                    cpp.blockStmt(
                        translateStatement(stmt.body),
                    ),
                ),
            )
            is TmpL.ReturnStatement -> {
                val retExpr = stmt.expression
                // If returning a void variable reference, emit bare return
                val isVoidReturn = retExpr is TmpL.Reference &&
                    cpp.name(retExpr.id).id.text in voidVarNames
                // Check if returning 'this' — either via TmpL.This or TmpL.Reference to the this variable
                val isThisReturn = currentThisVarName != null && (
                    retExpr is TmpL.This ||
                        (retExpr is TmpL.Reference && cpp.name(retExpr.id).id.text == currentThisVarName)
                    )
                val translatedRet = when {
                    isVoidReturn -> null
                    isThisReturn -> {
                        // Use coerce() to handle structural interface casts
                        cpp.callExpr(
                            cpp.name(TEMPER_CORE_NAMESPACE, "coerce"),
                            listOf(translateExpression(retExpr)),
                        )
                    }
                    else -> translateExpressionOrNull(retExpr)
                }
                listOf(cpp.returnStmt(translatedRet))
            }

            is TmpL.SetAbstractProperty -> translateSetProperty(stmt.left, stmt.right, useSetterMethod = true)
            is TmpL.SetBackedProperty -> translateSetProperty(stmt.left, stmt.right, useSetterMethod = false)
            is TmpL.ThrowStatement -> listOf(
                cpp.throwStmt(
                    cpp.callExpr(cpp.name(TEMPER_CORE_NAMESPACE, "TemperBubble"), emptyList()),
                ),
            )
            is TmpL.YieldStatement -> unsupportedStatement(stmt)
        }
    }

    private fun translatePropertyId(prop: TmpL.PropertyId): Cpp.SingleName = when (prop) {
        is TmpL.ExternalPropertyId -> cpp.singleName(CppName(fixName(prop.name.dotNameText)))
        is TmpL.InternalPropertyId -> {
            // A backing field is emitted under its property's dot name (see
            // translatePropertyMember / translateGetterMember), so resolve the access
            // through the same dotName registered in [propertyDotNames].
            // prepopulatePropertyDotNames seeds an entry for every property of the
            // enclosing type before any method body is translated, so a hit is the
            // normal case.
            val dotName = propertyDotNames[propKey(prop.name.name)]
            if (dotName != null) {
                cpp.singleName(CppName(fixName(dotName)))
            } else {
                // Not a registered property of this type (e.g. a synthesized internal
                // name whose resolved text already equals the field name). Fall back to
                // the resolved name directly.
                cpp.name(prop.name.name)
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
                is TmpL.SuperSubject -> error("super property handled elsewhere")
            }
            return listOf(cpp.exprStmt(call))
        }
        // Fall back to direct assignment
        val lhs: Cpp.Expr = when (val subj = lval.subject) {
            is TmpL.Expression -> cpp.op("->", translateExpression(subj), propSingleName)
            is TmpL.ConnectedToTypeName -> cpp.scopedName(translateTypeName(subj), propSingleName)
            is TmpL.TemperTypeName -> cpp.scopedName(translateTypeName(subj), propSingleName)
            is TmpL.SuperSubject -> error("illegal super call for backed property")
        }
        return listOf(
            cpp.exprStmt(cpp.op("=", lhs, translateExpression(right))),
        )
    }

    /** True if the block's control unconditionally leaves (so a trailing `break` is dead). */
    private fun blockEndsWithControlLeave(block: TmpL.BlockStatement): Boolean =
        when (block.statements.lastOrNull()) {
            is TmpL.ReturnStatement,
            is TmpL.BreakStatement,
            is TmpL.ContinueStatement,
            -> true
            else -> false
        }

    /** Emit a forward declaration for a local function: `std::function<Ret(Params...)> name` */
    private fun localFuncForwardDecl(stmt: TmpL.LocalFunctionDeclaration): Cpp.Stmt {
        val paramTypeStrs = stmt.parameters.parameters.joinToString(", ") { param ->
            renderToString(translateType(param.type))
        }
        val retTypeStr = renderToString(translateType(stmt.returnType))
        val funcTypeStr = "$STD_FUNCTION_PREFIX<$retTypeStr($paramTypeStrs)>"
        val nameStr = renderToString(cpp.name(stmt.name))
        return cpp.exprStmt(cpp.literal(cpp.raw("$funcTypeStr $nameStr")))
    }

    private fun translateBlock(block: TmpL.BlockStatement): Cpp.BlockStmt = cpp.pos(block) {
        val localFuncDecls = mutableListOf<Cpp.Stmt>()
        val stmts = mutableListOf<Cpp.Stmt>()
        val savedVoidVarNames = voidVarNames.toSet()
        val savedLocalFuncRefCaptures = localFuncRefCaptures.toList()
        val savedMutableLocalsInScope = mutableLocalsInScope.toMutableSet()
        localFuncRefCaptures = block.statements
            .filterIsInstance<TmpL.LocalFunctionDeclaration>()
            .map { decl -> renderToString(cpp.name(decl.name)) }
            .toMutableList()
        for (stmt in block.statements) {
            if (stmt is TmpL.LocalFunctionDeclaration) {
                localFuncDecls.add(localFuncForwardDecl(stmt))
            }
            stmts.addAll(translateStatement(stmt))
        }
        voidVarNames.clear()
        voidVarNames.addAll(savedVoidVarNames)
        localFuncRefCaptures = savedLocalFuncRefCaptures.toMutableList()
        mutableLocalsInScope = savedMutableLocalsInScope
        cpp.blockStmt(localFuncDecls + stmts)
    }

    private fun translateBlockWithThis(
        thisName: Cpp.SingleName,
        block: TmpL.BlockStatement,
    ): Cpp.BlockStmt = cpp.pos(block) {
        val savedThisVarName = currentThisVarName
        currentThisVarName = thisName.id.text
        val savedLocalFuncRefCaptures = localFuncRefCaptures.toList()
        val savedMutableLocalsInScope = mutableLocalsInScope.toMutableSet()
        localFuncRefCaptures = block.statements
            .filterIsInstance<TmpL.LocalFunctionDeclaration>()
            .map { decl -> renderToString(cpp.name(decl.name)) }
            .toMutableList()
        try {
            cpp.blockStmt(
                buildList {
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
                    for (stmt in block.statements) {
                        if (stmt is TmpL.LocalFunctionDeclaration) {
                            add(localFuncForwardDecl(stmt))
                        }
                    }
                    block.statements.forEach { stmt ->
                        addAll(translateStatement(stmt))
                    }
                },
            )
        } finally {
            currentThisVarName = savedThisVarName
            localFuncRefCaptures = savedLocalFuncRefCaptures.toMutableList()
            mutableLocalsInScope = savedMutableLocalsInScope
        }
    }

    /** True if [type] is a union that includes `Null` (i.e. already an optional type). */
    private fun isNullableType(type: TmpL.Type): Boolean = when (type) {
        is TmpL.TypeUnion -> type.types.any { t ->
            t is TmpL.NominalType && t.typeName.sourceDefinition == WellKnownTypes.nullTypeDefinition
        }
        else -> false
    }

    private fun isNullableType2(type: Type2): Boolean = type is NullableType

    /** Wraps an expression in NullableParam<type> unless the type is already nullable */
    private fun wrapInNullableParamIfNeeded(type: Type2, innerExpr: Cpp.Expr? = null): Cpp.Expr {
        return if (isNullableType2(type)) {
            // Type is already nullable (Nullable<T> = NullableParam<T> for value types)
            // Don't double-wrap
            innerExpr ?: cpp.literal(cpp.raw("nullptr"))
        } else {
            val typeExpr = translateType2(type)
            if (innerExpr != null) {
                cpp.callExpr(
                    cpp.template(
                        cpp.name(TEMPER_CORE_NAMESPACE, "NullableParam"),
                        listOf(typeExpr),
                    ),
                    innerExpr,
                )
            } else {
                cpp.callExpr(
                    cpp.template(
                        cpp.name(TEMPER_CORE_NAMESPACE, "NullableParam"),
                        listOf(typeExpr),
                    ),
                )
            }
        }
    }

    /** A reference-counted (shared_ptr) or std::function type — expensive to copy. */
    private fun isIndirectType(type: Cpp.Type): Boolean = when (type) {
        is Cpp.TemplateType -> leafNameOf(type.base) == "shared_ptr"
        is Cpp.SingleName -> type.id.text.startsWith(STD_FUNCTION_PREFIX)
        else -> false
    }

    /** `T const&` — used to pass indirect parameters without copying (no refcount churn). */
    private fun constRef(type: Cpp.Type): Cpp.Type =
        cpp.singleName(CppName("${renderToString(type)} const &", raw = true))

    private fun translateParamType(formal: TmpL.Formal): Cpp.Type {
        val baseType = translateType(formal.type)
        val withOptional = if (formal.optional && !isNullableType(formal.type.ot)) {
            // Only wrap in NullableParam if the type isn't already nullable
            // (Nullable<T> for value types is already NullableParam<T>)
            cpp.template(
                cpp.name(TEMPER_CORE_NAMESPACE, "NullableParam"),
                listOf(baseType),
            )
        } else {
            baseType
        }
        // Pass shared_ptr / std::function parameters by const reference to avoid a copy
        // (and its atomic refcount bump) on every call — but only when the parameter is
        // never reassigned, since a const reference can't be rebound. std::function's type
        // erasure means callers/function-values interoperate regardless of by-value vs
        // by-const-ref, so this stays a local change to function and method signatures.
        return if (formal.assignOnce && isIndirectType(withOptional)) constRef(withOptional) else withOptional
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
                val alreadyNullable = isNullableType(f.type.ot)
                if (idx < firstOptionalIdx + numProvided) {
                    // Wrap optional params in NullableParam (unless already nullable)
                    if (f.optional && !alreadyNullable) {
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
                    if (alreadyNullable) {
                        // Already nullable type — just pass nullptr
                        cpp.literal(cpp.raw("nullptr"))
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

    /**
     * Pre-populate [propertyDotNames] (and getter method names for abstract getters)
     * from a type's members so method bodies can resolve backing field names
     * regardless of member ordering in TmpL.
     */
    private fun prepopulatePropertyDotNames(topLevel: TmpL.TypeDeclaration) {
        for (member in topLevel.members) {
            when (member) {
                is TmpL.Property -> {
                    propertyDotNames[propKey(member.name.name)] = member.dotName.dotNameText
                }
                is TmpL.Getter -> {
                    if (member.propertyShape.abstractness != Abstractness.Concrete) {
                        val getterDotName = member.dotName.dotNameText
                        val propDotName = getterDotName.removePrefix("get.")
                        propertyDotNames.getOrPut(propKey(member.name.name)) { propDotName }
                        val getterCppName = getterSingleName(getterDotName)
                        getterMethodNames[propDotName] = getterCppName
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Emit a static property member: a `static` struct field declaration plus its
     * out-of-line definition (with initializer) in the .cpp.
     */
    private fun MutableList<Cpp.StructPart>.translateStaticPropertyMember(
        member: TmpL.StaticProperty,
        topLevel: TmpL.TypeDeclaration,
        impl: MutableList<Cpp.Global>,
    ) {
        propertyDotNames[propKey(member.name.name)] = member.dotName.dotNameText
        val propCppName = CppName(fixName(member.dotName.dotNameText))
        val typeStr = renderToString(translateType(member.type))
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
                init = translateExpression(member.expression),
            ),
        )
    }

    /**
     * Emit a regular (instance) property member as a struct field, unless the property
     * is abstract (in which case the backing field is provided by a concrete subtype).
     */
    private fun MutableList<Cpp.StructPart>.translatePropertyMember(member: TmpL.Property) {
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

    /**
     * Emit a getter member: a backed getter returning the field for concrete properties,
     * or a pure-virtual / bodied accessor (using the `get_` prefix) for abstract ones.
     */
    private fun MutableList<Cpp.StructPart>.translateGetterMember(
        member: TmpL.Getter,
        topLevel: TmpL.TypeDeclaration,
        isInterface: Boolean,
        realSuperTypes: List<TmpL.NominalType>,
        isTemplate: Boolean,
        impl: MutableList<Cpp.Global>,
        templateMethodDefs: MutableList<Cpp.Global>,
    ) {
        // Generate getter for concrete (backed) properties
        // Always generate to ensure GetAbstractProperty
        // finds the method regardless of type hierarchy
        if (member.propertyShape.abstractness == Abstractness.Concrete) {
            val needsVirtual = isInterface ||
                realSuperTypes.any()
            // Generate getter that returns backing field
            val getterDotName = member.dotName.dotNameText
            // Ensure getter method name differs from field name
            val getterCppName = getterSingleName(getterDotName)
            val propDotName = getterDotName.removePrefix("get.")
            getterMethodNames[propDotName] = getterCppName
            propertyDotNames.getOrPut(propKey(member.name.name)) { propDotName }
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
                // Backed getter reads a field: non-mutating, so const.
                qual = memberConstQualifier(getterDotName),
            )
            emitMethodDeclAndDef(func, isTemplate, needsVirtual, impl, templateMethodDefs)
        } else {
            // Abstract getter — use get_ prefix to avoid
            // field/method name collision in subtypes
            val getterDotName = member.dotName.dotNameText
            val getterCppName = getterSingleName(getterDotName)
            val propDotName = getterDotName.removePrefix("get.")
            getterMethodNames[propDotName] = getterCppName
            propertyDotNames.getOrPut(propKey(member.name.name)) { propDotName }
            when (val body = member.body) {
                null -> {
                    // Pure-virtual getter has no body to mutate: const
                    // when the property's slot is non-mutating.
                    add(
                        pureVirtualMethod(
                            translateType(member.returnType),
                            getterCppName,
                            qual = memberConstQualifier(getterDotName),
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
                                val type = translateParamType(param)
                                val name = cpp.name(param.name)
                                type to name
                            }
                        },
                        translateBlockWithThis(
                            cpp.name(member.parameters.parameters.first().name),
                            body,
                        ),
                        qual = memberConstQualifier(getterDotName),
                    )
                    val needsVirtualGetter = isInterface ||
                        realSuperTypes.any()
                    emitMethodDeclAndDef(func, isTemplate, needsVirtualGetter, impl, templateMethodDefs)
                }
            }
        }
    }

    /**
     * Emit a setter member: an override doing direct field assignment for concrete
     * backed properties under inheritance, or a pure-virtual / bodied setter (using the
     * `set_` prefix) for abstract ones. Tracks already-declared setters in [declaredSetters].
     */
    private fun MutableList<Cpp.StructPart>.translateSetterMember(
        member: TmpL.Setter,
        topLevel: TmpL.TypeDeclaration,
        isInterface: Boolean,
        realSuperTypes: List<TmpL.NominalType>,
        isTemplate: Boolean,
        impl: MutableList<Cpp.Global>,
        templateMethodDefs: MutableList<Cpp.Global>,
        declaredSetters: MutableSet<String>,
    ) {
        if (member.propertyShape.abstractness == Abstractness.Concrete) {
            if (isInterface || realSuperTypes.any()) {
                // Generate an override setter that does direct field assignment
                val setterCppName = setterSingleName(member.dotName.dotNameText)
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
                            val type = translateParamType(param)
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
                val firstDecl = setterKey !in declaredSetters
                if (firstDecl) declaredSetters.add(setterKey)
                emitMethodDeclAndDef(func, isTemplate, true, impl, templateMethodDefs, emitDecl = firstDecl)
            }
            // else: no supertypes, backed properties use direct field access
        } else {
            // Abstract setter — use set_ prefix to avoid
            // field/method name collision in subtypes
            val setterDotName = member.dotName.dotNameText
            val setterCppName = setterSingleName(setterDotName)
            val propDotName = setterDotName.removePrefix("set.")
            setterMethodNames[propDotName] = setterCppName
            val setterKey = setterCppName.id.text
            when (val body = member.body) {
                null -> {
                    if (setterKey !in declaredSetters) {
                        declaredSetters.add(setterKey)
                        val paramTypes = member.parameters.parameters.drop(1).map {
                            translateParamType(it)
                        }
                        add(
                            pureVirtualMethod(
                                translateType(member.returnType),
                                setterCppName,
                                paramTypes.mapIndexed { i, t ->
                                    cpp.funcParam(t, cpp.singleName(CppName("arg_$i")))
                                },
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
                                val type = translateParamType(param)
                                val name = cpp.name(param.name)
                                type to name
                            }
                        },
                        translateBlockWithThis(
                            cpp.name(member.parameters.parameters.first().name),
                            body,
                        ),
                    )
                    val needsVirtualSetter = isInterface ||
                        realSuperTypes.any()
                    val firstDecl = setterKey !in declaredSetters
                    if (firstDecl) declaredSetters.add(setterKey)
                    emitMethodDeclAndDef(
                        func, isTemplate, needsVirtualSetter, impl, templateMethodDefs, emitDecl = firstDecl,
                    )
                }
            }
        }
    }

    /**
     * Emit a normal (instance) method member: a pure-virtual declaration for abstract
     * methods, otherwise a virtual-as-needed definition plus any optional-parameter overloads.
     */
    private fun MutableList<Cpp.StructPart>.translateNormalMethodMember(
        member: TmpL.NormalMethod,
        topLevel: TmpL.TypeDeclaration,
        isInterface: Boolean,
        realSuperTypes: List<TmpL.NominalType>,
        isTemplate: Boolean,
        impl: MutableList<Cpp.Global>,
        templateMethodDefs: MutableList<Cpp.Global>,
    ) {
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
        // Emit `const` for a method the mutation analysis shows never
        // mutates its receiver. (Generators and optional-param methods are
        // seeded as mutating in that analysis, so they resolve to null here.)
        val methodQual = memberConstQualifier(member.dotName.dotNameText)
        when (val body = member.body) {
            null -> {
                val paramTypes = member.parameters.parameters.drop(1).map {
                    translateParamType(it)
                }
                add(
                    pureVirtualMethod(
                        translateType(member.returnType),
                        methodCppName0,
                        paramTypes.mapIndexed { i, t ->
                            cpp.funcParam(t, cpp.singleName(CppName("arg_$i")))
                        },
                        qual = methodQual,
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
                    realSuperTypes.any()
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
                        cpp.name(member.parameters.parameters.first().name),
                        body,
                    ),
                    qual = methodQual,
                )
                emitMethodDeclAndDef(func, isTemplate, needsVirtual, impl, templateMethodDefs)
                if (hasOptional) {
                    val scopedName = cpp.scopedName(
                        cpp.name(topLevel.name),
                        methodCppName.deepCopy(),
                    )
                    val retType =
                        translateType(member.returnType)
                    emitMethodOverloads(
                        generateOptionalOverloads(scopedName, retType, methodFormals.toList()),
                        isTemplate, impl, templateMethodDefs,
                    )
                }
            }
        }
    }

    /**
     * Emit a static method member: skips abstract statics, otherwise a `static` definition
     * plus any optional-parameter overloads.
     */
    private fun MutableList<Cpp.StructPart>.translateStaticMethodMember(
        member: TmpL.StaticMethod,
        topLevel: TmpL.TypeDeclaration,
        isTemplate: Boolean,
        impl: MutableList<Cpp.Global>,
        templateMethodDefs: MutableList<Cpp.Global>,
    ) {
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
                emitMethodDeclAndDef(func, isTemplate, false, impl, templateMethodDefs, declMod = Cpp.DefMod.Static)
                if (hasOptional) {
                    val scopedName = cpp.scopedName(
                        cpp.name(topLevel.name),
                        methodCppName.deepCopy(),
                    )
                    val retType =
                        translateType(member.returnType)
                    emitMethodOverloads(
                        generateOptionalOverloads(
                            scopedName, retType, methodFormals.toList(), declMod = Cpp.DefMod.Static,
                        ),
                        isTemplate, impl, templateMethodDefs,
                    )
                }
            }
        }
    }

    /**
     * Emit a constructor member as a static `make()` factory that stack-constructs the
     * value, runs the body, and returns a `make_shared` of it, plus optional-parameter overloads.
     */
    private fun MutableList<Cpp.StructPart>.translateConstructorMember(
        member: TmpL.Constructor,
        topLevel: TmpL.TypeDeclaration,
        impl: MutableList<Cpp.Global>,
    ) {
        val thisParam = member.parameters.parameters.first()
        val thisName = cpp.name(thisParam.name)
        val typeName = cpp.name(topLevel.name)
        val resultName = cpp.tmp("result")
        val makeName = cpp.singleName(CppName("make"))
        val constructorFormals =
            member.parameters.parameters.drop(1)
        val params = constructorFormals.map {
            cpp.pos(it) {
                cpp.funcParam(translateParamType(it), cpp.name(it.name))
            }
        }
        val paramTypes = constructorFormals.map {
            cpp.pos(it) { translateParamType(it) }
        }
        val objectType = sharedPtr(typeName)
        val body = cpp.pos(member.body) {
            cpp.blockStmt(
                buildList {
                    // Construct the object on the heap up front so `this` points at the very
                    // instance the returned shared_ptr owns. A previous version built a stack
                    // temporary, took its address as `this`, and copied it into a shared_ptr on
                    // return: that left `this` dangling if the body let it escape, and gave the
                    // returned object a different identity than the one the body mutated.
                    add(
                        cpp.varDef(
                            objectType.deepCopy(),
                            resultName,
                            cpp.callExpr(
                                cpp.template(
                                    cpp.name("std", "make_shared"),
                                    listOf(typeName),
                                ),
                            ),
                        ),
                    )
                    add(
                        cpp.varDef(
                            cpp.ptr(typeName),
                            thisName,
                            cpp.literal(
                                cpp.raw(
                                    "${resultName.id.text}.get()",
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
                    add(cpp.returnStmt(resultName.deepCopy()))
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

    /**
     * Emit the struct definition for a type declaration: forward declaration, struct body
     * (with base specifiers for inherited/interface types), and out-of-line template method
     * definitions rewritten with `ClassName<T...>::method` scoping for template structs.
     */
    private fun emitStructDefinition(
        topLevel: TmpL.TypeDeclaration,
        isInterface: Boolean,
        realSuperTypes: List<TmpL.NominalType>,
        structFields: List<Cpp.StructPart>,
        templateMethodDefs: List<Cpp.Global>,
        headerTypeDecl: MutableList<Cpp.Global>,
        headerTypeDefs: MutableList<Cpp.Global>,
    ) {
        val structName = cpp.name(topLevel.name)
        val struct = cpp.struct(structName, structFields)
        val superTypes = realSuperTypes

        // The base-class specifiers for this struct. A single base is inherited `virtual`
        // so the shared `AnyValueBase` subobject stays unique under diamond inheritance;
        // multiple bases are plain `public`. An interface with no declared supertypes
        // virtually inherits the common `AnyValueBase` root.
        fun baseSpecs(): List<Cpp.BaseSpec> = if (superTypes.isNotEmpty()) {
            superTypes.map { cpp.baseSpec(virtual = superTypes.size == 1, base = translateSuperType(it)) }
        } else {
            listOf(cpp.baseSpec(virtual = true, base = cpp.name(TEMPER_CORE_NAMESPACE, "AnyValueBase")))
        }
        val typeFormals = topLevel.typeParameters.ot.typeParameters

        // A plain (rootless) reference struct — no supertypes and not an interface — does not
        // share the `AnyValueBase` root that inheritance/interface types use, so it carries its
        // own CRTP `enable_shared_from_this<Self>`. That lets `borrow_this` recover a properly
        // owning shared_ptr for `this` (objects are created via make_shared) instead of a
        // non-owning alias that would dangle if a callee stored it. Non-virtual: each plain
        // struct is the unique enable_shared_from_this in its (single-node) hierarchy.
        fun enableSharedFromThisBase(): Cpp.BaseSpec {
            val selfType: Cpp.Type = if (typeFormals.isNotEmpty()) {
                cpp.template(structName.deepCopy(), typeFormals.map { cpp.name(it.name) })
            } else {
                structName.deepCopy()
            }
            return cpp.baseSpec(
                virtual = false,
                base = cpp.template(cpp.name("std", "enable_shared_from_this"), listOf(selfType)),
            )
        }
        if (typeFormals.isNotEmpty()) {
            val templateParams = typeFormals.map { formal ->
                cpp.funcParam(
                    cpp.singleName(CppName("class", allowKey = true)),
                    cpp.name(formal.name),
                )
            }
            // For template structs, skip the forward declaration
            // and emit the full template struct definition
            val structDefToUse: Cpp.AnyStructDef = if (superTypes.isNotEmpty() || isInterface) {
                cpp.derivedStructDef(structName, baseSpecs(), structFields)
            } else {
                // No supertypes: carry a CRTP enable_shared_from_this base (see above).
                cpp.derivedStructDef(structName, listOf(enableSharedFromThisBase()), structFields)
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
                                    // Preserve the const qualifier so the out-of-line
                                    // template method definition matches its declaration.
                                    qual = methodDef.qual,
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
        } else if (superTypes.isNotEmpty() || isInterface) {
            // Class with inheritance (one or more super types) or an interface, which
            // virtually inherits the common AnyValueBase root.
            headerTypeDecl.add(struct.decl)
            headerTypeDefs.add(
                cpp.derivedStructDef(structName, baseSpecs(), structFields),
            )
        } else {
            // No supertypes: carry a CRTP enable_shared_from_this base (see above) so
            // `borrow_this` can hand out an owning shared_ptr for `this`.
            headerTypeDecl.add(struct.decl)
            headerTypeDefs.add(
                cpp.derivedStructDef(structName, listOf(enableSharedFromThisBase()), structFields),
            )
        }
    }

    /**
     * Translate a single TmpL type declaration (class or interface), appending its members to the
     * given header/implementation sections: per-member declarations and definitions are emitted via
     * the `translate*Member` helpers, then [emitStructDefinition] assembles the struct itself.
     */
    private fun translateTypeDeclaration(
        topLevel: TmpL.TypeDeclaration,
        impl: MutableList<Cpp.Global>,
        headerTypeDecl: MutableList<Cpp.Global>,
        headerTypeDefs: MutableList<Cpp.Global>,
    ) {
        val isInterface = topLevel.kind == TmpL.TypeDeclarationKind.Interface
        val superTypes = topLevel.superTypes
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
        prepopulatePropertyDotNames(topLevel)
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
                        is TmpL.StaticProperty ->
                            translateStaticPropertyMember(member, topLevel, impl)
                        is TmpL.Property ->
                            translatePropertyMember(member)
                        is TmpL.Getter ->
                            translateGetterMember(
                                member, topLevel, isInterface, superTypes,
                                isTemplate, impl, templateMethodDefs,
                            )
                        is TmpL.Setter ->
                            translateSetterMember(
                                member, topLevel, isInterface, superTypes,
                                isTemplate, impl, templateMethodDefs, declaredSetters,
                            )
                        is TmpL.NormalMethod ->
                            translateNormalMethodMember(
                                member, topLevel, isInterface, superTypes,
                                isTemplate, impl, templateMethodDefs,
                            )
                        is TmpL.StaticMethod ->
                            translateStaticMethodMember(
                                member, topLevel, isTemplate, impl, templateMethodDefs,
                            )
                        is TmpL.Constructor ->
                            translateConstructorMember(member, topLevel, impl)
                        is TmpL.GarbageStatement -> {
                            add(cpp.comment("skipped: GarbageStatement"))
                        }
                    }
                }
            }
        }
        emitStructDefinition(
            topLevel, isInterface, superTypes, structFields,
            templateMethodDefs, headerTypeDecl, headerTypeDefs,
        )
        // Clean up type formal names after type declaration
        for (formal in structTypeFormals) {
            typeFormalNames.remove(formal.definition)
        }
        for (key in structTypeFormalKeys) {
            typeFormalNamesByText.remove(key)
        }
    }

    /**
     * Translate a module-level function declaration, emitting its (possibly template)
     * definition and any optional-parameter overloads into the header/impl sections.
     */
    private fun translateModuleFunction(
        topLevel: TmpL.ModuleFunctionDeclaration,
        headerFunctions: MutableList<Cpp.Global>,
        impl: MutableList<Cpp.Global>,
    ) {
        val formals = topLevel.parameters.parameters
        val hasOptional = formals.any { it.optional }
        val typeFormals =
            topLevel.typeParameters.ot.typeParameters
        // Populate type formal map BEFORE translating
        // return type, param types, and body
        val savedTypeFormalNames = mutableMapOf<TypeDefinition, Cpp.SingleName>()
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
            paramTypes + sharedPtr(stdVector(elemType))
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
        for (formal in typeFormals) {
            typeFormalNames.remove(formal.definition)
        }
        for (key in savedTypeFormalKeys) {
            typeFormalNamesByText.remove(key)
        }
    }

    /**
     * Translate a module-level variable declaration, forward-declaring it (or as `static`)
     * and deferring its initializer assignment into [deferredInitStmts] to avoid SIOF.
     * Imported names and non-"real" (e.g. void/intersection) types are skipped.
     */
    private fun translateModuleLevelDeclaration(
        topLevel: TmpL.ModuleLevelDeclaration,
        hasTemplateFunctions: Boolean,
        headerDecl: MutableList<Cpp.Global>,
        implVarDecls: MutableList<Cpp.Global>,
        deferredInitStmts: MutableList<Cpp.Stmt>,
    ) {
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
                // Always declare with null init and defer assignment
                // to the init function to avoid SIOF.
                if (isExported || hasTemplateFunctions) {
                    headerDecl.add(cpp.varDecl(type, name))
                    implVarDecls.add(cpp.varDef(type, name, null))
                } else {
                    implVarDecls.add(
                        cpp.varDef(
                            Cpp.DefMod.Static, type, name, null,
                        ),
                    )
                }
                if (translatedInit != null) {
                    deferredInitStmts.add(
                        cpp.exprStmt(
                            cpp.binaryExpr(
                                cpp.literal(cpp.raw(name.id.text)),
                                cpp.binaryOp("="),
                                translatedInit,
                            ),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Translate a test top-level into a free function, emitting it and recording its
     * fully-qualified name in [testInfos] for later main.cpp generation.
     */
    private fun translateModuleTest(
        topLevel: TmpL.Test,
        headerFunctions: MutableList<Cpp.Global>,
        impl: MutableList<Cpp.Global>,
    ) {
        // Tests are translated as functions
        val cppName = cpp.name(topLevel.name)
        val func = cpp.func(
            cppName,
            translateType(topLevel.returnType),
            topLevel.parameters.parameters.map { translateParamType(it) },
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
            else -> "$libNs::${cppName.id.text}"
        }
        testInfos.add(qualifiedName to topLevel.rawName)
    }

    /**
     * Gather the fully-qualified `temper_init_*` calls for every imported dependency module,
     * adding each dependency's header to [includes] as a side effect.
     */
    private fun gatherDependencyInitCalls(): MutableSet<String> {
        val depInitCalls = mutableSetOf<String>()
        for ((_, info) in importedNames) {
            val depModName = info.sourceModule
            val libNs = cpp.nameTextForModule(depModName)
            val depRelPath = depModName.relativePath()
            val depBaseName = when {
                depRelPath.segments.isEmpty() -> INIT_NAME
                else -> depRelPath.segments.last().baseName
            }
            val sanitizedDep = depBaseName.replace(Regex("[^a-zA-Z0-9_]"), "_")
            depInitCalls.add("$libNs::temper_init_$sanitizedDep")
            // Add include for the dependency module's header so its init decl is visible
            includes.add(cpp.includePathForModule(depModName))
        }
        return depInitCalls
    }

    /**
     * Emit the deferred `temper_init_<module>()` function (header decl + impl def) that guards
     * against double init, calls dependency module inits, then runs the deferred variable
     * initializations. Sets [moduleInitFuncName] as a side effect.
     */
    private fun emitModuleInit(
        sanitizedModuleName: String,
        deferredInitStmts: List<Cpp.Stmt>,
        headerInit: MutableList<Cpp.Global>,
        impl: MutableList<Cpp.Global>,
    ) {
        // Generate module init function with dependency init calls
        // and deferred variable initializations.
        val initFuncName = cpp.singleName(CppName("temper_init_$sanitizedModuleName"))
        moduleInitFuncName = initFuncName.id.text

        val bodyStmts = mutableListOf<Cpp.Stmt>()
        // static bool guard — prevent double initialization
        bodyStmts.add(
            cpp.exprStmt(
                cpp.literal(
                    cpp.raw(
                        "static bool initialized = false",
                    ),
                ),
            ),
        )
        bodyStmts.add(
            cpp.ifStmt(
                cpp.literal(cpp.raw("initialized")),
                cpp.blockStmt(listOf(cpp.returnStmt(null))),
            ),
        )
        bodyStmts.add(
            cpp.exprStmt(
                cpp.binaryExpr(
                    cpp.literal(cpp.raw("initialized")),
                    cpp.binaryOp("="),
                    cpp.literal(cpp.raw("true")),
                ),
            ),
        )
        // Call dependency modules' init functions using fully qualified names.
        val depInitCalls = gatherDependencyInitCalls()
        for (call in depInitCalls.sorted()) {
            bodyStmts.add(
                cpp.exprStmt(
                    cpp.callExpr(
                        cpp.literal(cpp.raw(call)),
                        listOf(),
                    ),
                ),
            )
        }
        // Add deferred variable initializations (for modules without init blocks)
        bodyStmts.addAll(deferredInitStmts)

        val voidType = cpp.type("void")
        // Declaration in header
        headerInit.add(
            cpp.funcDecl(
                mod = null,
                ret = voidType,
                name = initFuncName,
                args = listOf(),
            ),
        )
        // Definition in cpp — placed at the beginning so the
        // dependency-trigger struct constructor can call it.
        val initFuncDef = cpp.funcDef(
            ret = voidType,
            name = initFuncName,
            args = listOf(),
            body = cpp.blockStmt(bodyStmts),
        )
        // Add init function definition at the end of the impl file (after
        // all static variable declarations so it can reference them).
        // No auto-trigger struct — init must be called explicitly from
        // main() or the generated main.cpp. Auto-trigger via static
        // constructors causes cross-TU init order issues: std::function
        // variables have non-trivial default constructors that can zero
        // out values set by an earlier cross-TU init call.
        impl.add(initFuncDef)
    }

    fun translateModule(mod: TmpL.Module): List<Backend.TranslatedFileSpecification> {
        includes.clear()
        currentModuleLocation = mod.codeLocation.codeLocation
        preprocessImports(mod)
        propertyDotNames.clear()
        getterMethodNames.clear()
        setterMethodNames.clear()
        voidVarNames.clear()
        narrowingContext.clear()
        currentThisVarName = null
        typeFormalNames.clear()
        typeFormalNamesByText.clear()
        testInfos.clear()
        moduleInitFuncName = null

        fun namespaced(body: Iterable<Cpp.Global>): Iterable<Cpp.Global> = cpp.pos(mod) {
            val innerNamespace = when (cppLibraryName) {
                null -> body
                else -> listOf(cpp.namespace(cpp.libraryName(cppLibraryName), body))
            }
            return@pos innerNamespace
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

            val implVarDecls = mutableListOf<Cpp.Global>()
            val impl = mutableListOf<Cpp.Global>()

            // Pre-scan: check if module has template functions.
            // If so, non-exported module-level variables need extern
            // declarations in the header (template function bodies
            // in the header can reference them).
            val hasTemplateFunctions = mod.topLevels.any { tl ->
                tl is TmpL.ModuleFunctionDeclaration &&
                    tl.typeParameters.ot.typeParameters.isNotEmpty()
            }

            // All variable initializations and init blocks are deferred to
            // the init function body to avoid the Static Initialization Order
            // Fiasco (SIOF). Variables are forward-declared in implVarDecls,
            // and their assignments + init block code go here in source order.
            val deferredInitStmts = mutableListOf<Cpp.Stmt>()

            // Compute sanitized module name for the init function.
            val moduleBaseName = when {
                path.segments.isNotEmpty() -> path.segments.last().baseName
                else -> INIT_NAME
            }
            val sanitizedModuleName = moduleBaseName.replace(Regex("[^a-zA-Z0-9_]"), "_")

            mod.topLevels.forEach { topLevel ->
                cpp.pos(topLevel) {
                    when (topLevel) {
                        is TmpL.EmbeddedComment -> {}
                        is TmpL.ModuleInitBlock -> {
                            val block = translateBlock(topLevel.body)
                            deferredInitStmts.addAll(block.stmts)
                        }
                        is TmpL.ModuleFunctionDeclaration ->
                            translateModuleFunction(topLevel, headerFunctions, impl)
                        is TmpL.ModuleLevelDeclaration ->
                            translateModuleLevelDeclaration(
                                topLevel, hasTemplateFunctions, headerDecl,
                                implVarDecls, deferredInitStmts,
                            )
                        is TmpL.TypeDeclaration ->
                            translateTypeDeclaration(topLevel, impl, headerTypeDecl, headerTypeDefs)
                        is TmpL.TypeConnection -> {
                            // Type connections are handled by the support network
                        }
                        is TmpL.PooledValueDeclaration -> {
                            // Pooled values are inlined at use sites
                        }
                        is TmpL.SupportCodeDeclaration -> {
                            // Support code declarations are handled inline
                        }
                        is TmpL.Test ->
                            translateModuleTest(topLevel, headerFunctions, impl)
                        is TmpL.GarbageTopLevel -> {
                            // Skip garbage
                        }
                        is TmpL.BoilerplateCodeFoldBoundary -> {
                            // Skip boilerplate markers
                        }
                    }
                }
            }

            emitModuleInit(sanitizedModuleName, deferredInitStmts, headerInit, impl)

            val modPath = mod.codeLocation.outputPath
            val hppName = path.withTemperAwareExtension(HPP_EXT)

            listOf(
                Backend.TranslatedFileSpecification(
                    path = path.withTemperAwareExtension(HPP_EXT),
                    mimeType = MimeType.cppSource,
                    content = cpp.program(
                        buildList {
                            add(cpp.pragma(cpp.raw("once")))
                            add(cpp.include("temper-core/core.hpp"))
                            for (inc in includes.sorted()) {
                                add(cpp.include(inc))
                            }
                            addAll(namespaced(header()))
                        },
                    ),
                ),
                Backend.TranslatedFileSpecification(
                    path = path.withTemperAwareExtension(CPP_EXT),
                    mimeType = MimeType.cppSource,
                    content = cpp.program(
                        buildList {
                            add(cpp.include("$modPath$hppName"))
                            addAll(namespaced(implVarDecls + impl))
                        },
                    ),
                ),
            )
        }
    }
}
