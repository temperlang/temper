package lang.temper.log

import lang.temper.common.sprintf

/**
 * Kinds of log messages.
 */
enum class MessageTemplate(
    /** Allows producing a human-readable string.  Form is à la [sprintf]. */
    override val formatString: String,
    /** The stage that produces this error. */
    val stage: CompilationPhase,
) : MessageTemplateI {
    LibraryFound("Library found", CompilationPhase.Staging),
    LibraryDropped("Library dropped", CompilationPhase.Staging),
    PreStagingModule("Pre-staging module from %s", CompilationPhase.Staging),
    StartingStage("Starting stage %s", CompilationPhase.Staging),
    StagePromotionFailed("Promotion to stage %s failed", CompilationPhase.Staging),
    LibraryConfigured("Configured library %s", CompilationPhase.Staging),
    AllDone("Bye", CompilationPhase.Staging),
    NonValueResultFromRunStage(
        "Run completed without a value result, %s",
        CompilationPhase.RuntimeEmulation,
    ),
    RunEndedWithUnresolvedPromise(
        "Run ended with unresolved promises",
        CompilationPhase.RuntimeEmulation,
    ),
    UnresolvedPromisesPreventedCompletion(
        "Execution blocked on unresolved promise",
        CompilationPhase.RuntimeEmulation,
    ),
    AsyncTaskFailedSilently(
        "An async task failed which may prevent normal completion of other tasks",
        CompilationPhase.RuntimeEmulation,
    ),
    TooManySuperTokens(
        "Cannot segment Temper source file with more than two `;;;` tokens",
        CompilationPhase.Staging,
    ),
    ImportFailed("Import of %s failed", CompilationPhase.Staging),
    ImportFromFailed("Import of %s from %s failed", CompilationPhase.Staging),
    SkewedSourceContent(
        "Content of source file has changed on disk which could affect error message snippets",
        CompilationPhase.Staging,
    ),
    MalformedLibraryName(
        "Malformed library name `%s` should be a dashed identifier",
        CompilationPhase.Staging,
    ),
    MalformedAssignment("Malformed assignment", CompilationPhase.Staging),
    DuplicateLibraryName(
        "Library name %s is used by multiple libraries at %s",
        CompilationPhase.CodeGeneration,
    ),
    ReadFailed("Read of %s failed", CompilationPhase.Staging),

    // Lexical problems
    BadEmoji("Emoji not allowed", CompilationPhase.Lex),
    InvalidIdentifier("Not a valid identifier", CompilationPhase.Lex),
    MalformedNumber("Malformed number", CompilationPhase.Lex),
    MalformedSemilit("Malformed semi-literate boundary", CompilationPhase.Lex),
    UnclosedBlock("Open bracket has no closing bracket", CompilationPhase.Lex),
    UnclosedQuotation("Missing close quote", CompilationPhase.Lex),
    UnmatchedBracket("Close bracket matches no open bracket", CompilationPhase.Lex),
    UnnormalizedIdentifier("Identifier is not in Unicode normal form NFKC", CompilationPhase.Lex),
    UnrecognizedStringEscape("Unrecognized escape sequence in quoted string", CompilationPhase.Lex),
    UnrecognizedToken("Syntax error", CompilationPhase.Lex),

    // Parsing problems
    TooFewOperands("Operator %s expects at least %d operands but got %d", CompilationPhase.Parse),
    TooManyOperands("Operator %s expects at most %d operands but got %d", CompilationPhase.Parse),
    ExtraneousModuleContent("Extraneous module content", CompilationPhase.Parse),
    InvalidMetadata("Module metadata is not well-formed JSON", CompilationPhase.Parse),
    SyntaxReservedForTuples(
        "`(...,...)` syntax is reserved for tuples.  Use `do {...}` to group statements",
        CompilationPhase.Parse,
    ),

    // Tree building problems
    Unparsable("Expected a %s here", CompilationPhase.TreeBuild),
    ClassPropertyMustAppearFirst(
        "In {...} object syntax, any `class` property must appear first",
        CompilationPhase.TreeBuild,
    ),

    // Detected during string unpacking, but only reported later.
    InvalidUnicode("Invalid Unicode scalar value", CompilationPhase.TreeBuild),
    InvalidUnicodeBecauseLarge("Invalid Unicode scalar value, too large", CompilationPhase.TreeBuild),
    InvalidUnicodeBecauseSurrogate(
        "Only Unicode scalar values are allowed, not surrogate code points",
        CompilationPhase.TreeBuild,
    ),
    InvalidUnicodeBecauseSurrogatePair(
        "Instead of surrogate pair, use single code point: \\u{%s}",
        CompilationPhase.TreeBuild,
    ),

    // During interpretation
    Interpreting("Interpreting", CompilationPhase.Interpreter),
    UndeclaredName("%s has not been declared", CompilationPhase.Interpreter),
    InconvertibleValue("Could not convert (%s) to a value", CompilationPhase.Interpreter),
    UserMessage("%s", CompilationPhase.Interpreter),
    IsNotAName("Expected a name", CompilationPhase.Interpreter),
    CouldNotSetLocal("Failed to assign %s", CompilationPhase.Interpreter),
    ExpectedValueOfType("Expected value of type %s not %s", CompilationPhase.Interpreter),
    NoCalleeMatching("No callee matches inputs %s among %s", CompilationPhase.Interpreter),
    AlreadyDeclared("Name was already declared", CompilationPhase.Interpreter),
    ClassMemberNameConflict("Class members with same name conflict at %s", CompilationPhase.Interpreter),
    OverrideLowersVisibility("Override has lower visibility than in %s", CompilationPhase.Interpreter),
    MissingMemberVisibility(
        "Members of class %s require explicit visibility: %s",
        CompilationPhase.Interpreter,
    ),
    TypeCheckRejected("Type %s rejected value %s", CompilationPhase.Interpreter),
    BuiltinEnvironmentIsNotMutable(
        "Cannot declare locals (e.g. %s) in the builtin environment",
        CompilationPhase.Interpreter,
    ),
    DeclarationHasTooManyNames("Declaration has too many names %s", CompilationPhase.Interpreter),
    MalformedDeclaration("Declaration is malformed", CompilationPhase.Interpreter),
    MalformedFunction("Function is malformed", CompilationPhase.Interpreter),
    MalformedSpecial("Use of builtin %s is malformed", CompilationPhase.Interpreter),
    MalformedTypeDeclaration("Type declaration is malformed", CompilationPhase.Interpreter),
    MalformedTypeMember("Type member is malformed", CompilationPhase.Interpreter),
    MalformedType("Type is malformed", CompilationPhase.Interpreter),
    MalformedStatement("Malformed statement", CompilationPhase.Interpreter),
    OptionalArgumentTooEarly("Optional arguments must follow required arguments", CompilationPhase.Interpreter),
    MissingName("Missing a name", CompilationPhase.Interpreter),
    Uninitialized("%s has not been initialized", CompilationPhase.Interpreter),
    OfDeclarationInitializerDisallowed(
        "Initializer not allowed on `of` declaration",
        CompilationPhase.Interpreter,
    ),
    ExpectedDeclarationForOf("Declaration required for `of`", CompilationPhase.Interpreter),

    /**
     * Position is the position of the downstream use.
     * Second argument is a list of positions of branches that fail to initialize.
     */
    UseBeforeInitialization("%s is not initialized along branches at %s", CompilationPhase.Interpreter),
    ArityMismatch("Wrong number of arguments.  Expected %d", CompilationPhase.Interpreter),
    Incomparable("Cannot compare %s and %s", CompilationPhase.Interpreter),
    DivByZero("Division by zero", CompilationPhase.Interpreter),
    NoSignatureMatches("No signature matches", CompilationPhase.Interpreter),
    MultipleConstructorSignaturesMatch(
        "Multiple types have matching constructors: %s",
        CompilationPhase.Interpreter,
    ),
    FailedToComputeDefault("Failed to compute default value for %s", CompilationPhase.Interpreter),
    NoValuePassed("No value passed for %s", CompilationPhase.Interpreter),
    MissingArgument("No argument passed for %s", CompilationPhase.Interpreter),
    RedundantArgument("Argument passed but it matches no parameter", CompilationPhase.Interpreter),
    MissingDeclaration("No declaration for %s", CompilationPhase.Interpreter),
    MissingProperty("No property %s declared in type %s", CompilationPhase.Interpreter),
    PropertyNotInitializedInConstructor("Property %s not initialized in constructor", CompilationPhase.Interpreter),
    CannotSetAbstractProperty("Cannot assign abstract property %s", CompilationPhase.Interpreter),
    CannotInstantiateAbstractType("Cannot instantiate abstract type %s", CompilationPhase.Interpreter),
    MissingType("No type for %s", CompilationPhase.Interpreter),
    UnsupportedByInterpreter("TODO %s", CompilationPhase.Interpreter), // TODO obviate this
    SignatureMismatch("Arguments did not match function signature", CompilationPhase.Interpreter),
    InvalidBlockContent("Invalid block content", CompilationPhase.Interpreter),
    InvalidCaseCondition("Invalid case condition", CompilationPhase.Interpreter),
    ElseMustBeLast("Other cases are invalid after else", CompilationPhase.Interpreter),
    MissingCaseValue("Missing case value", CompilationPhase.Interpreter),
    UnableToEvaluate("Unable to evaluate", CompilationPhase.Interpreter),
    UnresolvedJumpTarget("break/continue not within a matching block", CompilationPhase.Interpreter),
    IncompleteDeclaration(
        "Cannot initialize an incomplete declaration",
        CompilationPhase.Interpreter,
    ),
    MalformedActual(
        "Formal argument where actual expected.  `:` only applies to function parameters",
        CompilationPhase.Interpreter,
    ),
    NamedActual(
        "Actual arguments cannot be provided by name",
        CompilationPhase.Interpreter,
    ),
    NoArgumentNamed("No argument named %s", CompilationPhase.Interpreter),
    TooManyArgumentsNamed("Too many arguments named %s", CompilationPhase.Interpreter),
    ActualNotInBounds(
        "Type formal <%s> cannot bind to %s which does not fit upper bounds %s",
        CompilationPhase.Interpreter,
    ),
    Unreached("Never reached by macro expander %s", CompilationPhase.Interpreter),
    Aborted("Interpretation aborted", CompilationPhase.Interpreter),
    MalformedFlow("Block has broken flow graph", CompilationPhase.Interpreter),
    InterpreterCannotEvaluateErrorExpression(
        "Interpreter encountered error()",
        CompilationPhase.Interpreter,
    ),
    CannotInvokeMacroAsFunction("Cannot invoke macro as function", CompilationPhase.Interpreter),
    NotApplicable("Cannot apply %s to %s:%s", CompilationPhase.Interpreter),
    InternalInterpreterError("Internal error: %s", CompilationPhase.Interpreter),
    CouldNotStoreFailureBit(
        "Internal error: failed to set failure bit",
        CompilationPhase.Interpreter,
    ),
    ReturnOutsideFn("Return outside function body", CompilationPhase.Interpreter),
    YieldingOutsideGeneratorFn("%s outside generator function body", CompilationPhase.Interpreter),
    ThisOutsideClassBody(
        "`this` may only appear inside a type definition",
        CompilationPhase.Interpreter,
    ),
    CannotExtend(
        "A class may not extend %s. Only named types and And(`&`) types may be extended",
        CompilationPhase.Interpreter,
    ),
    CannotExtendConcrete(
        "Cannot extend concrete type(s) %s",
        CompilationPhase.Interpreter,
    ),
    CannotExtendSealed(
        "Cannot extend sealed type %s from %s. %s is not declared in the same module.",
        CompilationPhase.Interpreter,
    ),
    CannotSealClass("Only interfaces can be sealed", CompilationPhase.Interpreter),
    CannotIntroduceParamInSealedSubtype(
        "Cannot introduce type parameters in sealed subtype %s",
        CompilationPhase.Interpreter,
    ),
    ConstructorArgumentInInterfaceType(
        "Constructor parameters are not allowed on interface types",
        CompilationPhase.Interpreter,
    ),
    TypeParameterInInterfaceMethod(
        "Illegal type parameter %s. Overridable methods don't allow generics",
        CompilationPhase.Interpreter,
    ),
    NotConstructible(
        "%s is not a concrete type with a constructor",
        CompilationPhase.Interpreter,
    ),
    CannotConnectToTestRunner(
        "Cannot connect to test runner",
        CompilationPhase.Interpreter,
    ),
    MalformedAnnotation("Malformed annotation", CompilationPhase.Interpreter),
    UnexpectedMetadata("Metadata %s has unexpected value %s", CompilationPhase.Interpreter),
    MemberUnavailable("Class member %s is unavailable", CompilationPhase.Interpreter),
    StaticMemberNeedsQualified("Type name required for accessing static member", CompilationPhase.Interpreter),
    StaticMemberUsesChaining("Static member access should use `.`, not `?.`", CompilationPhase.Interpreter),
    NoAccessibleMember("No accessible member %s in type %s", CompilationPhase.Interpreter),
    NoAccessibleGetter("No accessible getter %s in %s", CompilationPhase.Interpreter),
    NoAccessibleSetter("No accessible setter %s in %s", CompilationPhase.Interpreter),
    CannotResetConst("Cannot set const %s again", CompilationPhase.Interpreter),
    DidNotStayConsistent("Tree did not stay consistent", CompilationPhase.Interpreter),
    CannotCaptureMultiplyDeclared(
        "Cannot capture %s because it is also declared at %s",
        CompilationPhase.Interpreter,
    ),
    OutOfBounds("Out of bounds.  %s not in %s", CompilationPhase.Interpreter),
    BadImportEnvironment(
        "Module not loaded in an environment that allows import",
        CompilationPhase.Interpreter,
    ),
    ImportPathHasTooManyParentParts(
        "Import path has too many \"..\" path segments: %s",
        CompilationPhase.Interpreter,
    ),
    MalformedImportPathSegmentUtf8(
        "Import path does not URL decode to valid UTF-8: `%s` from `%s`",
        CompilationPhase.Interpreter,
    ),
    MalformedImportPathSegment(
        "Import path segment is invalid: `%s` from `%s`",
        CompilationPhase.Interpreter,
    ),
    BreakingImportCycle(
        "Module %s imported itself via chain of imports %s",
        CompilationPhase.Interpreter,
    ),
    InImportCycle("Import was involved in cycle", CompilationPhase.Interpreter),

    CannotExport(
        "Cannot export non-parsed name",
        CompilationPhase.Interpreter,
    ),
    NotExported(
        "%s does not export symbol %s",
        CompilationPhase.Interpreter,
    ),
    ExportNeedsNonExported(
        "Export depends publicly on non-exported symbol %s",
        CompilationPhase.Interpreter,
    ),
    NoSymbolForImport(
        "No symbol for import from %s",
        CompilationPhase.Interpreter,
    ),
    ImplicitsUnavailable(
        "Implicit imports are unavailable due to an internal compiler error",
        CompilationPhase.Interpreter,
    ),
    MultipleRenames("Extra rename not allowed", CompilationPhase.Interpreter),
    WildcardWithoutImport("Wildcard destructure allowed only for import", CompilationPhase.Interpreter),
    IllegalAssignment(
        "Cannot assign to %s from %s",
        CompilationPhase.Interpreter,
    ),
    IllegalReassignment(
        "%s is reassigned after %s but is not declared `var` at %s",
        CompilationPhase.Interpreter,
    ),
    IncompatibleUsage(
        "Member %s defined in %s incompatible with usage",
        CompilationPhase.CodeGeneration,
    ),
    ExpectedSubType(
        "Expected subtype of %s, but got %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedStructure(
        "Expected structure %s",
        CompilationPhase.Interpreter,
    ),
    SignatureInputMismatch(
        "Actual arguments do not match signature: %s expected %s, but got %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedNonVoid(
        "Void expressions cannot be used as values",
        CompilationPhase.Interpreter,
    ),
    ExpectedNoBubble(
        "Cannot bubble from a function without Bubble in its return type",
        CompilationPhase.Interpreter,
    ),
    TooLateForMacro(
        "Macro %s cannot expand at %s; must expand at or before %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedFunctionType(
        "Expected function type, but got %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedType(
        "Expected type expression, but got %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedNominalType(
        "Expected named type, but got %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedTypeShape(
        "Expected a class or interface type, but got %s",
        CompilationPhase.Interpreter,
    ),
    TypeActualsUnavailable(
        "Could not infer type actuals for %s given %s in context %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedConstant(
        "Expected constant expression, but got %s",
        CompilationPhase.Interpreter,
    ),
    ExpectedBlock("Expected block", CompilationPhase.Interpreter),
    UnnecessaryRttiCheck(
        "Unnecessary type check to %s from expression with type %s which is a subtype",
        CompilationPhase.Interpreter,
    ),
    IllegalRttiCheckUpDown(
        "Unrelated types cannot be targeted with is or as runtime type checks: <%s> from %s",
        CompilationPhase.Interpreter,
    ),
    IllegalRttiCheckActuals(
        // Can't cast from B<T> (or no args) to A<T, U> (with any args unmentioned in B).
        "Type arguments cannot be introduced with is or as runtime type checks: <%s> from %s",
        CompilationPhase.Interpreter,
    ),
    IllegalRttiCheckFormals(
        // Can't cast to type parameter T.
        "Type parameters cannot be targeted with is or as runtime type checks: <%s> from %s",
        CompilationPhase.Interpreter,
    ),
    IllegalRttiCheckMayDowncast(
        "Types marked @mayDowncastTo(false) cannot be targeted with is or as " +
            "runtime type checks because they may not be distinct on all backends: <%s> from %s",
        CompilationPhase.Interpreter,
    ),
    IllegalRttiCheckConnected(
        "Connected types cannot be targeted with is or as runtime type checks" +
            " because multiple Temper types are allowed to connect to the same backend type: <%s> from %s",
        CompilationPhase.Interpreter,
    ),
    ImpossibleRttiCheck(
        "Runtime type check from %s to %s can never succeed",
        CompilationPhase.Interpreter,
    ),
    NotInStatementPosition(
        "This is only allowed in statement position in documentation fragments",
        CompilationPhase.Interpreter,
    ),
    MixinCannotBeReincorporated(
        "Mixin from %s could not be reincorporated due to staging errors",
        CompilationPhase.Interpreter,
    ),
    BadQName(
        "Malformed qualified name `%s`",
        CompilationPhase.Interpreter,
    ),

    // Code generation
    NotGeneratingCodeFor("Not generating code for %s", CompilationPhase.CodeGeneration),
    FailedToWrite("I/O error writing %s", CompilationPhase.CodeGeneration),
    TranslationReady("Translation by %s ready for %s", CompilationPhase.CodeGeneration),
    CannotTranslate("Cannot translate %s", CompilationPhase.CodeGeneration),
    BadBackend("Cannot configure backend %s", CompilationPhase.CodeGeneration),
    MissingLibrary("No such library `%s`", CompilationPhase.Staging),
    MissingLibraryConfiguration("Missing library configuration", CompilationPhase.CodeGeneration),
    BadPublicationHistory(
        "Bad publication history for library %s",
        CompilationPhase.CodeGeneration,
    ),
    UnexpectedException("Unexpected exception: %s", CompilationPhase.CodeGeneration),

    // Runtime emulation
    StandardOut("Print: %s", CompilationPhase.RuntimeEmulation),

    // Documentation generation errors
    UnableToCompileFragmentTemperCode(
        "Unable to compile fragment `%s` which was definitely temper code",
        CompilationPhase.CodeGeneration,
    ),
    UnableToCompileFragmentMaybeTemperCode(
        "Unable to compile fragment `%s` which was maybe temper code",
        CompilationPhase.CodeGeneration,
    ),

    // Language server messages.
    CreateLibraryConfig(
        """No library config found for "%s". Create one in workspace folder "%s"?""",
        CompilationPhase.Tooling,
    ),
    CreateLibraryConfigYes("Yes, Create It", CompilationPhase.Tooling),
    CreateLibraryConfigNo("No, Don't", CompilationPhase.Tooling),
    CreateLibraryConfigFailed("Failed to create: %s", CompilationPhase.Tooling),
}

enum class CompilationPhase {
    Staging,
    Lex,
    Parse,
    TreeBuild,
    Interpreter, // TODO: rename to Frontend?
    CodeGeneration,
    RuntimeEmulation,
    Tooling,
}
