package lang.temper.value

import lang.temper.lexer.Operator
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.Symbol

// Names and symbols for builtins

/**
 * The name of the pseudo-type member used in `expr as TypeToCastTo` and in
 * renames like `let { a as b } = c;`.
 */
val asSymbol = Symbol("as")

/** The name of the pseudo-type member used in `expr is TypeToCheckInstanceOf`. */
val isSymbol = Symbol("is")

/**
 * Marks blocks that need disambiguation between actual arguments to a function call and
 * formal arguments in a function definition.  See the *Arg* production in *Grammar*.
 */
val complexArgSymbol = Symbol("_complexArg_")
val vComplexArgSymbol = Value(complexArgSymbol)
val concreteSymbol = Symbol("concrete")
val vConcreteSymbol = Value(concreteSymbol)

/** Marks the condition portion of a `for (init;cond;incr){body}` loop. */
val condSymbol = Symbol("cond")
val vCondSymbol = Value(condSymbol)

/**
 * As a key, marks the default expression for formal function parameters.
 * As a value, after [visibilitySymbol] indicates the default visibility.
 */
val defaultSymbol = Symbol("default")
val vDefaultSymbol = Value(defaultSymbol)

/**
 * This symbol is associated with a string value that allows backends to connect
 * the declaration to a target language definition.
 */
val connectedSymbol = Symbol("connected")
val vConnectedSymbol = Value(connectedSymbol)

/**
 * This symbol is associated with a boolean value, and may decorate a type declaration.
 *
 * If the boolean is true, then the type's values must be distinguishable from other
 * type's values in all backends, so may be used with RTTI casts and checks like
 * the `is Type` and `as Type` pseudo-methods.
 *
 * If the boolean is false, then the type's values may not be distinguishable from other
 * type's values.
 *
 * If the metadata is absent on a type declaration, then whether it may be downcast to
 * is inferred based on rules described in the documentation for the `@mayDowncastTo`
 * decorator.
 */
val mayDowncastToSymbol = Symbol("mayDowncastTo")
val vMayDowncastToSymbol = Value(mayDowncastToSymbol)

/** The type from which a closure converted member declaration was extracted. */
val fromTypeSymbol = Symbol("fromType")
val vFromTypeSymbol = Value(fromTypeSymbol)

/** Mark implicit declarations as such to help tools. */
val implicitSymbol = Symbol("implicit")
val vImplicitSymbol = Value(implicitSymbol)

/**
 * Marks an export as implicitly imported only if mentioned.
 * See comments about REPL over-connecting in *ImportStage*.
 */
val optionalImportSymbol = Symbol("optionalImport")
val vOptionalImportSymbol = Value(optionalImportSymbol)

/**
 * Indicates that the parameter is a `this` value inserted into the parameter list as part of type
 * definition processing.
 */
val impliedThisSymbol = Symbol("impliedThis")
val vImpliedThisSymbol = Value(impliedThisSymbol)

/** Marks the increment portion of a `for (init;cond;incr){body}` loop. */
val incrSymbol = Symbol("incr")
val vIncrSymbol = Value(incrSymbol)

/**
 * Marks the initial value of a declaration.
 */
val initSymbol = Symbol("init")
val vInitSymbol = Value(initSymbol)

/**
 * Marks the init portion of a `for (init;cond;incr){body}` loop.
 * This is called "flow init", not "for init" since it is meant to be generally applicable to flow
 * control like constructs.
 */
val flowInitSymbol = Symbol("__flowInit")
val vFlowInitSymbol = Value(flowInitSymbol)

/** Indicates that the next argument is interpolated into some kind of text literal. */
val interpolateSymbol = Symbol("interpolate")

/**
 * At the beginning of a block with a linear flow, precedes the label for the block to which code in
 * the block can `break`.
 */
val labelSymbol = Symbol("label")
val vLabelSymbol = Value(labelSymbol)
val optionalSymbol = Symbol("optional")
val vOptionalSymbol = Value(optionalSymbol)
val callJoinSymbol = Symbol(Operator.CallJoin.text!!)

/**
 * The [symbol][lang.temper.type.MemberShape.symbol] for the `constructor` special method.
 */
val constructorSymbol = Symbol("constructor")
val vConstructorSymbol = Value(constructorSymbol)

/**
 * On a declaration in a class body, indicates that it defines a method.
 */
val methodSymbol = Symbol("method")
val vMethodSymbol = Value(methodSymbol)

/**
 * Marks declarations that represent named functions, methods, or static methods;
 * any declaration that reliably has a function tree as its initializer and which does
 * not have [varSymbol].
 *
 * After typing,
 * declarations without this have names whose descriptors are types,
 * and declarations with this have names whose descriptors are signatures.
 *
 * See [staticPropertySymbol]
 */
val fnSymbol = Symbol("fn")
val vFnSymbol = Value(fnSymbol)

/**
 * On a declaration in a class body that defines a method, indicates the method is a getter and
 * the value corresponds to the name of the abstract property for which it is a getter.
 */
val getterSymbol = Symbol("getter")
val vGetterSymbol = Value(getterSymbol)

/**
 * On a declaration in a class body that defines a method, indicates the method is a setter and
 * the value corresponds to the name of the abstract property for which it is a setter.
 */
val setterSymbol = Symbol("setter")
val vSetterSymbol = Value(setterSymbol)

/**
 * Comments are converted to calls to REM which, during the syntax stage, are attached to
 * declarations using this metadata allowing the [Helpful] mechanism access to comments, and
 * allowing backends, like the Python backend, to integrate with their target language's REPL
 * help system.
 *
 * The metadata value associated with this is a [TList] with three elements:
 *
 * 1. A brief help string.  Normally the first paragraph of the comment text interpreted as
 *    markdown with comment delimiters, and line prefixes, and any documentation tool
 *    annotations removed.
 * 2. A long help string.  Normally the entire comment text with the same content removed.
 * 3. Location metadata which may help resolve relative link targets in the same.
 */
val docStringSymbol = Symbol("docString")
val vDocStringSymbol = Value(docStringSymbol)

/** Symbol for `else` argument to `if` builtin. */
val elseSymbol = Symbol("else")

/** Symbol for `else_if` argument to `if` builtin formed from merging the two tokens `else` `if`. */
val elseIfSymbol = Symbol("else_if")
val caseSymbol = Symbol("case")
val caseIsSymbol = Symbol("case_is")
val caseCaseSymbol = Symbol("case_case")
val outTypeSymbol = Symbol("outType")
val vOutTypeSymbol = Value(outTypeSymbol)

/**
 * Indicates that a block lambda representing a generator function has been recognized
 * as such and has had a wrapper function inserted around it that receives the arguments
 * meant for the generator instance.
 */
val wrappedGeneratorFnSymbol = Symbol("wrappedGeneratorFn")
val vWrappedGeneratorFnSymbol = Value(wrappedGeneratorFnSymbol)

/**
 * Indicates to the resolve-names pass that a declaration with a parsed name has a known resolution.
 */
val resolutionSymbol = Symbol("resolution")
val vResolutionSymbol = Value(resolutionSymbol)

/**
 * On a declaration in a class body, indicates that it defines a property.
 */
val propertySymbol = Symbol("property")
val vPropertySymbol = Value(propertySymbol)

/**
 * On a declaration in a class body, indicates that it defines a static property.
 * Static methods are a subset of properties that are known to initialize to a function value
 * and are *additionally* marked with the [fnSymbol].
 */
val staticPropertySymbol = Symbol("staticProperty")
val vStaticPropertySymbol = Value(staticPropertySymbol)

/**
 * A type formal (see [typeFormalSymbol]) that is declared on a class/interface type,
 * not on a function or method.
 *
 *     interface I<T> { // Yes, <T> is a member type formal.
 *       f<U>(): Void;  // No, <U> is a type formal on a member but not a member type formal.
 *     }
 *     let g<V>() {...} // No, <V> is a type formal but is not even enclosed by a type.
 */
val memberTypeFormalSymbol = Symbol("memberTypeFormal")
val vMemberTypeFormalSymbol = Value(memberTypeFormalSymbol)

/**
 * This symbol marks backed property shapes and the associated boolean indicates whether
 * they correspond to an input in the default constructor.
 *
 * Consider two class definitions.
 *
 *     class C1(let x: Int = 0)
 *
 *     class C2 { let x: Int = 0; }
 *
 * `C1.x` is a constructorProperty, `C2.x` is not.
 *
 * In the first, the default constructor expects an optional input `x`. The `0` is the
 * default expression for that input.
 *
 * In the second, the default constructor initializes `this.x` using `0`, the initializer
 * expression.
 *
 * So the constructorPropertySymbol marks a semantic difference appears in how a
 * default constructor is generated, and also in how property bag expressions like `{x: 12}`
 * are resolved to constructor invocations.
 *
 * This metadata is removed after the default constructor is generated.
 *
 * Those class definitions, with default constructors, become:
 *
 *     class C1 {
 *       let x: Int;
 *       public constructor(x: Int = 0): Void {
 *         this.x = x;
 *       }
 *     }
 *
 *     class C2 {
 *       let x: Int;
 *       public constructor(): Void {
 *         this.x = 0;
 *       }
 *     }
 */
val constructorPropertySymbol = Symbol("constructorProperty")
val vConstructorPropertySymbol = Value(constructorPropertySymbol)

/**
 * Metadata for an operator that opts constructor arguments out from having
 * a corresponding backed property auto-generated.
 */
val noPropertySymbol = Symbol("noProperty")

val privateSymbol = Symbol("private")
val vPrivateSymbol = Value(privateSymbol)
val protectedSymbol = Symbol("protected")
val vProtectedSymbol = Value(protectedSymbol)
val publicSymbol = Symbol("public")
val vPublicSymbol = Value(publicSymbol)
val reifiesSymbol = Symbol("reifies")
val vReifiesSymbol = Value(reifiesSymbol)
val returnDeclSymbol = Symbol("returnDecl")
val vReturnDeclSymbol = Value(returnDeclSymbol)

/**
 * Symbol that marks whether [FunTree]s can be the target of a `return` statement.
 *
 *     let f(): Int {
 *       if (cond) {
 *         return 1;
 *       }
 *       g();
 *       2
 *     }
 *
 * In the above, during early processing stages, there are multiple FunTrees:
 *
 * 1. the named function declaration `let f(): Int { ... }`
 * 2. the anonymous block lambda passed to the `if` macro: `{ return 1; }`
 *
 * The `return` is lexically within the second but means to return from the
 * first which works because, at later stages, `if` inlines its clauses.
 *
 * Simple `return` syntax returns from the closest enclosing [FunTree]
 * marked with metadata using this symbol and the value true,
 * which block lambdas do not have.
 */
val returnedFromSymbol = Symbol("returnedFrom")
val vReturnedFromSymbol = Value(returnedFromSymbol)

/**
 * Indicates that the decorated declaration has a single assignment.
 *
 * The presence of this symbol means there is a single expression subtree that controls the value
 * associated with the declaration.
 *
 * It does not mean that it is only assigned once or that it is assigned before first use.
 *
 * The analysis is flow sensitive so treats:
 *
 *     let i;
 *     while (c) {
 *        i = 0;
 *     }
 *
 * as having a single assignment that may be entered zero or more times for each time that control
 * passes through `let i`.
 *
 * Also, a single assignment is found in:
 *
 *     let i;
 *     if (false) {
 *         i = 0;
 *     }
 */
val ssaSymbol = Symbol("ssa")
val vSsaSymbol = Value(ssaSymbol)
val stageRangeSymbol = Symbol("stageRange")
val vStageRangeSymbol = Value(stageRangeSymbol)
val staticSymbol = Symbol("static")
val vStaticSymbol = Value(staticSymbol)
val staySymbol = Symbol("stay")
val vStaySymbol = Value(staySymbol)

/** Marks a forbids type relationship */
val forbidsSymbol = Symbol("forbids")
val vForbidsSymbol = Value(forbidsSymbol)

/** Marks a supports type relationship */
val supportsSymbol = Symbol("supports")
val vSupportsSymbol = Value(supportsSymbol)

/** Marks a super type declaration */
val superSymbol = Symbol("super")
val vSuperSymbol = Value(superSymbol)
val surpriseMeSymbol = Symbol("...")
val vSurpriseMeSymbol = Value(surpriseMeSymbol)
val syntheticSymbol = Symbol("synthetic")
val vSyntheticSymbol = Value(syntheticSymbol)
val testSymbol = Symbol("test")
val vTestSymbol = Value(testSymbol)
val typeSymbol = Symbol("type")
val vTypeSymbol = Value(typeSymbol)

/**
 * A decorator may assume that a declaration marked with this is the name by which code outside
 * the type definition will refer to it, and that its value will be a reified type.
 */
val typeDeclSymbol = Symbol("typeDecl")
val vTypeDeclSymbol = Value(typeDeclSymbol)
val typeDefinedSymbol = Symbol("typeDefined")
val vTypeDefinedSymbol = Value(typeDefinedSymbol)

/**
 * TmpL needs to know about all types declared in a module, but type processing may leave no trace,
 * especially for nested, member-less interfaces.  The type placeholder symbol serves as an indication
 * to TmpL that a type definition is here.
 */
val typePlaceholderSymbol = Symbol("typePlaceholder")
val vTypePlaceholderSymbol = Value(typePlaceholderSymbol)

/**
 * Marks a syntactic structure that has not yet been converted to either a formal or an
 * actual type parameter.
 * See *TypeArgument* in Grammar.
 */
val typeArgSymbol = Symbol("typeArg")
val vTypeArgSymbol = Value(typeArgSymbol)

/**
 * Marks a declaration as declaring a formal type parameter as part of a
 * function's, method's, or class/interface type's declarations.
 * These are typically created from syntactic structures marked with [typeArgSymbol] during
 * the DisAmbiguate stage.
 */
val typeFormalSymbol = Symbol("typeFormal")
val vTypeFormalSymbol = Value(typeFormalSymbol)

/**
 * Indicates the type is a sealed type.
 * See builtin/@sealed for more details.
 */
val sealedTypeSymbol = Symbol("sealedType")
val vSealedTypeSymbol = Value(sealedTypeSymbol)

/**
 * That the associated argument is a variable length argument that the caller supplies as a comma separated set of
 * arguments that the function itself sees as a list
 */
val restFormalSymbol = Symbol("restFormal")
val vRestFormalSymbol = Value(restFormalSymbol)

/** Indicates that the associated declaration may be assigned more than once. */
val varSymbol = Symbol("var")
val vVarSymbol = Value(varSymbol)

/** Indicates that the associated declaration tracks failure information. */
val failSymbol = Symbol("fail")
val vFailSymbol = Value(failSymbol)

/** Indicates a filled-in missing operand to enable continued stage processing. */
val missingSymbol = Symbol("")
val vMissingSymbol = Value(missingSymbol)

/**
 * A symbol for declarations that we might add [varSymbol] to if there's an inherited setter.
 * We'll know that in the *Define* stage.
 */
val maybeVarSymbol = Symbol("maybeVar")
val vMaybeVarSymbol = Value(maybeVarSymbol)
val varianceSymbol = Symbol("variance")
val vVarianceSymbol = Value(varianceSymbol)
val visibilitySymbol = Symbol("visibility")
val vVisibilitySymbol = Value(visibilitySymbol)

/** Name of `while` parameter to the `do` builtin. */
val whileSymbol = Symbol("while")
val vWhileSymbol = Value(whileSymbol)

/**
 * Marks a declaration as being boilerplate content that should not be visible in
 * [translated documentation][lang.temper.lexer.Genre.Documentation] output.
 */
val withinDocFoldSymbol = Symbol("withinDocFold")
val vWithinDocFoldSymbol = Value(withinDocFoldSymbol)

/** Used to stores the parsed name for a function or declaration as a symbol. */
val wordSymbol = Symbol("word")
val vWordSymbol = Value(wordSymbol)

/**
 * Used to associate the string form of an [lang.temper.name.QName] of a declaration
 * or named function expression that appears lexically within the source text.
 */
val qNameSymbol = Symbol("QName")
val vQNameSymbol = Value(qNameSymbol)

/**
 * For declaration metadata, causes a disAmbiguate stage micro-pass to move them to the top of the
 * enclosing block.
 */
val hoistToBlockSymbol = Symbol("hoistToBlock")
val vHoistToBlockSymbol = Value(hoistToBlockSymbol)

/**
 * For declaration metadata, causes a syntaxMacro stage micro-pass to move them left within the
 * enclosing block so that the declaration is visible.
 * This is used to allow mutually referencing definitions as in
 * ```
 * interface I { j: J }
 * interface J { i: I } // J's definition has a backwards reference to type I.
 * // They cannot be reordered to allow only forward reference because I's definition refers to J.
 * ```
 */
val hoistLeftSymbol = Symbol("hoistLeft")
val vHoistLeftSymbol = Value(hoistLeftSymbol)

/**
 * Marks the declaration associated with a nominal type as the definition of an `enum` type.
 * This allows backends to identify which type definitions to translate to definitions that are
 * `enum`-esque.
 */
val enumTypeSymbol = Symbol("enumType")
val vEnumTypeSymbol = Value(enumTypeSymbol)

/**
 * Marks a declaration of a [static property][staticPropertySymbol] as a member of an
 * [`enum` type][enumTypeSymbol].
 */
val enumMemberSymbol = Symbol("enumMember")
val vEnumMemberSymbol = Value(enumMemberSymbol)

/**
 * A member name for methods used when desugaring square bracket reads like `x[key]`.
 */
val getSymbol = Symbol("get")
val vGetSymbol = Value(getSymbol)

/**
 * A member name for methods used when desugaring square bracket writes like `x[key] = newValue`.
 */
val setSymbol = Symbol("set")
val vSetSymbol = Value(setSymbol)

/** For indicating the source of an import. */
val importedSymbol = Symbol("imported")
val vImportedSymbol = Value(importedSymbol)

/** Reachability of declarations. Reachable from exports is the default once analysis is run. */
val reachSymbol = Symbol("reach")
val vReachSymbol = Value(reachSymbol)

/** Available for general use. For reachability, indicates unreachable. */
val noneSymbol = Symbol("none")
val vNoneSymbol = Value(noneSymbol)

/** Metadata key for the `@inlineUnrealizedGoal` decorator. */
val inlineUnrealizedGoalSymbol = Symbol("inlineUnrealizedGoal")
val vInlineUnrealizedGoalSymbol = Value(inlineUnrealizedGoalSymbol)

/** The dot name used when an extension function is invoked via `subject.dotName(...)` syntax. */
val extensionSymbol = Symbol("extension")
val vExtensionSymbol = Value(extensionSymbol)

/**
 * Key for [ParameterNameSymbols] metadata.
 * A list of required parameter symbols, in declaration order followed by null followed by
 * optional parameter symbols, also in declaration order.
 */
val parameterNameSymbolsListSymbol = Symbol("parameterNameSymbolsList")
val vParameterNameSymbolsListSymbol = Value(parameterNameSymbolsListSymbol)

/**
 * Metadata that marks an `interface` type as functional.
 *
 * See the `@fun` builtin.
 */
val functionalInterfaceSymbol = Symbol("functionalInterface")

/**
 * The receiver type when an extension (see [extensionSymbol]) function is invoked via
 * `ReceiverType.dotName(...)` syntax.
 */
val staticExtensionSymbol = Symbol("staticExtension")

/**
 * Marks types that need extra processing for JSON interop.
 * See [lang.temper.frontend.json]
 */
val jsonSymbol = Symbol("json")

/**
 * Metadata key for extra JSON properties that do not correspond to constructor
 * inputs but which may be used to disambiguate when decoding.
 * See [lang.temper.frontend.json]
 */
val jsonExtraSymbol = Symbol("jsonExtra")

/**
 * Metadata key for the JSON name for a property.
 * See [lang.temper.frontend.json]
 */
val jsonNameSymbol = Symbol("jsonName")

/** Provides a convenient default string representation. */
val toStringSymbol = Symbol("toString")
val vToStringSymbol = Value(toStringSymbol)

val consoleParsedName = ParsedName("console")
val defaultParsedName = ParsedName("default")
val fnParsedName = ParsedName("fn")
val thisParsedName = ParsedName("this")
val returnParsedName = ParsedName("return")
val staticParsedName = ParsedName("static")

val ampBuiltinName = BuiltinName("&")
val asBuiltinName = BuiltinName("as")
val assignBuiltinName = BuiltinName("=")
val atBuiltinName = BuiltinName("@")
val catBuiltinName = BuiltinName("cat")
val chainNullBuiltinName = BuiltinName("?.")
val classBuiltinName = BuiltinName("class")
val consoleBuiltinName = BuiltinName(consoleParsedName.nameText)
val crParsedName = ParsedName("cr") // For closure records
val curliesBuiltinName = BuiltinName("{}")
val squaresBuiltinName = BuiltinName("[]")
val doBuiltinName = BuiltinName("do")
val dotBuiltinName = BuiltinName(".")
val emptyBuiltinName = BuiltinName("empty")
val eqBuiltinName = BuiltinName("==")
val errorBuiltinName = BuiltinName("error")
val extendsBuiltinName = BuiltinName("extends")
val fnBuiltinName = BuiltinName("fn")
val getBuiltinName = BuiltinName("get")
val getConsoleBuiltinName = BuiltinName("getConsole")
val holeBuiltinName = BuiltinName("hole")
val ifBuiltinName = BuiltinName("if")
val importBuiltinName = BuiltinName("import")
val isBuiltinName = BuiltinName("is")
val letBuiltinName = BuiltinName("let")
val listBuiltinName = BuiltinName("list")
val listedTypeBuiltinName = BuiltinName("Listed")
val logicalOrBuiltinName = BuiltinName("||")
val newBuiltinName = BuiltinName("new")
val ofBuiltinName = BuiltinName("of")
val postfixApplyName = BuiltinName("postfixApply")
val quasiInnerBuiltinName = BuiltinName("quasiInner")
val quasiLeafBuiltinName = BuiltinName("quasiLeaf")
val rawBuiltinName = BuiltinName("raw")
val regexLiteralBuiltinName = BuiltinName("rgx")
val returnBuiltinName = BuiltinName(returnParsedName.nameText)
val setBuiltinName = BuiltinName("set")
val staticBuiltinName = BuiltinName("static")
val throwsBuiltinName = BuiltinName("throws")
val typeBuiltinName = BuiltinName("Type")
val unholeBuiltinName = BuiltinName("unhole")
val fileRestrictedBuiltinName = BuiltinName("__FILE__")
val getStaticBuiltinName = BuiltinName("getStatic")
val internalGetStaticBuiltinName = BuiltinName("igetStatic")
val pureVirtualBuiltinName = BuiltinName("pureVirtual")
val docStringDecoratorBuiltinName = BuiltinName("@docString")

/** Symbols that mark a declaration as a class or interface member. */
val typeMemberMetadataSymbols = setOf(
    constructorSymbol,
    getterSymbol,
    methodSymbol,
    propertySymbol,
    setterSymbol,
    staticPropertySymbol,
    memberTypeFormalSymbol,
)
